package com.picklerally.app.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.picklerally.app.R
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.MatchRecordMode
import com.picklerally.app.game.MatchValidator
import com.picklerally.app.game.ScoringMode
import com.picklerally.app.game.SubmittedMatchPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class StatsRepository(
    private val db: FirebaseFirestore,
    private val localPrefs: LocalPrefsStore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context, localPrefs: LocalPrefsStore) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
        localPrefs = localPrefs,
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun defaultDisplayName(user: FirebaseUser): String {
        val raw = user.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: user.email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Player-${user.uid.take(4)}"
        return raw.take(32)
    }

    suspend fun ensureUserProfile(
        user: FirebaseUser,
        fallbackCharacter: CharacterId,
    ): Result<CharacterId> {
        val uid = user.uid
        if (uid.isBlank()) {
            return Result.failure(IllegalStateException("Signed-in user has blank UID"))
        }
        val docPath = "users/$uid"
        return try {
            val localProfile = localPrefs.getLocalProfile(fallbackCharacter)
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                val resolvedChar = CharacterId.fromStringOrDefault(
                    existing?.characterId,
                    fallbackCharacter,
                )
                val cloudName = existing?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                if (cloudName != null) {
                    localPrefs.updateLocalDisplayName(cloudName, resolvedChar)
                }
                Result.success(resolvedChar)
            } else {
                val initialName = localProfile.displayName
                    .trim()
                    .takeIf { it.isNotEmpty() && it != "Local Player" }
                    ?.take(32)
                    ?: defaultDisplayName(user)
                val payload = mapOf(
                    "uid" to uid,
                    "displayName" to initialName,
                    "characterId" to fallbackCharacter.name,
                    "wins" to 0,
                    "losses" to 0,
                    "matchesPlayed" to 0,
                    "pointsScored" to 0,
                    "bestRally" to 0,
                    "rating" to 1000,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                localPrefs.updateLocalDisplayName(initialName, fallbackCharacter)
                Result.success(fallbackCharacter)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveUserCharacter(
        user: FirebaseUser?,
        characterId: CharacterId,
    ): Result<Unit> {
        localPrefs.setSelectedCharacter(characterId)
        val currentUser = user ?: return Result.success(Unit)
        val uid = currentUser.uid
        if (uid.isBlank()) return Result.success(Unit)
        val docPath = "users/$uid"
        return try {
            requireUserId()
            ensureUserProfile(currentUser, characterId)
            db.collection("users").document(uid).update(
                mapOf(
                    "characterId" to characterId.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            val lbRef = db.collection("leaderboard").document(uid)
            val lbSnap = lbRef.get().await()
            if (lbSnap.exists()) {
                lbRef.update(
                    mapOf(
                        "characterId" to characterId.name,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(
        user: FirebaseUser?,
        fallbackCharacter: CharacterId,
    ): Result<UserProfileStats> {
        if (user == null || user.uid.isBlank()) {
            return Result.success(localPrefs.getLocalProfile(fallbackCharacter))
        }
        val uid = user.uid
        val docPath = "users/$uid"
        return try {
            ensureUserProfile(user, fallbackCharacter).getOrThrow()
            val snap = db.collection("users").document(uid).get().await()
            val doc = snap.toObject(
                UserProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            Result.success(
                UserProfileStats(
                    uid = uid,
                    displayName = doc?.displayName?.takeIf { it.isNotBlank() }
                        ?: defaultDisplayName(user),
                    characterId = CharacterId.fromStringOrDefault(doc?.characterId, fallbackCharacter),
                    wins = (doc?.wins ?: 0L).toInt().coerceAtLeast(0),
                    losses = (doc?.losses ?: 0L).toInt().coerceAtLeast(0),
                    matchesPlayed = (doc?.matchesPlayed ?: 0L).toInt().coerceAtLeast(0),
                    pointsScored = (doc?.pointsScored ?: 0L).toInt().coerceAtLeast(0),
                    bestRally = (doc?.bestRally ?: 0L).toInt().coerceAtLeast(0),
                    rating = (doc?.rating ?: 1000L).toInt().coerceIn(800, 5000),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserDisplayName(
        user: FirebaseUser?,
        displayName: String,
        fallbackCharacter: CharacterId,
    ): Result<String> {
        val clean = displayName.trim().take(32)
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        }
        if (user == null || user.uid.isBlank()) {
            val saved = localPrefs.updateLocalDisplayName(clean, fallbackCharacter)
            return Result.success(saved)
        }

        val uid = user.uid
        val docPath = "users/$uid"
        return try {
            requireUserId()
            ensureUserProfile(user, fallbackCharacter).getOrThrow()
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            val lbRef = db.collection("leaderboard").document(uid)
            val lbSnap = lbRef.get().await()
            if (lbSnap.exists()) {
                lbRef.update(
                    mapOf(
                        "displayName" to clean,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            localPrefs.updateLocalDisplayName(clean, fallbackCharacter)
            Result.success(clean)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordFinishedMatch(
        user: FirebaseUser?,
        input: FinishedMatchInput,
    ): Result<RecordMatchOutcome> {
        val safeMatchId = "m_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val uid = user?.uid?.takeIf { it.isNotBlank() } ?: "local-player"

        val validation = MatchValidator.validateMatchPayload(
            SubmittedMatchPayload(
                matchId = safeMatchId,
                roomCode = input.roomCode,
                mode = input.mode,
                scoringMode = input.scoringMode,
                createdByUid = uid,
                participants = listOf(uid),
                scoreLower = input.scoreLower,
                scoreUpper = input.scoreUpper,
                winnerSide = input.winnerSide,
                bestRally = input.bestRally,
                playerCharacter = input.playerCharacter,
                partnerCharacter = input.partnerCharacter,
                opponentCharacter = input.opponentCharacter,
            ),
        )
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.reason))
        }

        val won = input.winnerSide == CourtSide.LOWER
        val ratingDelta = if (won) validation.winnerRatingDelta else validation.loserRatingDelta

        if (user == null || user.uid.isBlank()) {
            localPrefs.appendLocalMatch(safeMatchId, input, ratingDelta)
            return Result.success(RecordMatchOutcome(savedToCloud = false, matchId = safeMatchId))
        }

        val matchPath = "matches/$safeMatchId"
        return try {
            val signedInUid = requireUserId()
            ensureUserProfile(user, input.playerCharacter).getOrThrow()

            val matchPayload = buildMap<String, Any> {
                put("matchId", safeMatchId)
                if (!input.roomCode.isNullOrBlank()) {
                    put("roomCode", input.roomCode.take(16))
                }
                put("mode", input.mode.name)
                put("scoringMode", input.scoringMode.name)
                put("createdByUid", signedInUid)
                put("participants", listOf(signedInUid))
                put("playerCharacter", input.playerCharacter.name)
                if (input.partnerCharacter != null) {
                    put("partnerCharacter", input.partnerCharacter.name)
                }
                put("opponentCharacter", input.opponentCharacter.name)
                put("winnerSide", input.winnerSide.name)
                put("scoreLower", input.scoreLower.coerceIn(0, 99))
                put("scoreUpper", input.scoreUpper.coerceIn(0, 99))
                put("bestRally", input.bestRally.coerceIn(0, 999))
                put("validated", false)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("matches").document(safeMatchId).set(matchPayload).await()

            val userRef = db.collection("users").document(signedInUid)
            val userSnap = userRef.get().await()
            val prevUser = userSnap.toObject(
                UserProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            val prevWins = (prevUser?.wins ?: 0L).toInt().coerceAtLeast(0)
            val prevLosses = (prevUser?.losses ?: 0L).toInt().coerceAtLeast(0)
            val prevPoints = (prevUser?.pointsScored ?: 0L).toInt().coerceAtLeast(0)
            val prevBestRally = (prevUser?.bestRally ?: 0L).toInt().coerceAtLeast(0)
            val prevRating = (prevUser?.rating ?: 1000L).toInt().coerceIn(800, 5000)

            val nextWins = (prevWins + if (won) 1 else 0).coerceAtMost(100000)
            val nextLosses = (prevLosses + if (won) 0 else 1).coerceAtMost(100000)
            val nextMatchesPlayed = (nextWins + nextLosses).coerceAtMost(200000)
            val nextPoints = (prevPoints + input.scoreLower).coerceAtMost(5000000)
            val nextBestRally = max(prevBestRally, input.bestRally).coerceAtMost(999)
            val nextRating = MatchValidator.nextPlayerRating(prevRating, ratingDelta)
            val displayName = prevUser?.displayName?.takeIf { it.isNotBlank() }
                ?: defaultDisplayName(user)

            userRef.update(
                mapOf(
                    "displayName" to displayName,
                    "characterId" to input.playerCharacter.name,
                    "wins" to nextWins,
                    "losses" to nextLosses,
                    "matchesPlayed" to nextMatchesPlayed,
                    "pointsScored" to nextPoints,
                    "bestRally" to nextBestRally,
                    "rating" to nextRating,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            val lbRef = db.collection("leaderboard").document(signedInUid)
            val lbSnap = lbRef.get().await()
            if (lbSnap.exists()) {
                val prevLb = lbSnap.toObject(
                    LeaderboardDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                val existingLbWins = (prevLb?.wins ?: 0L).toInt().coerceAtLeast(0)
                val existingLbLosses = (prevLb?.losses ?: 0L).toInt().coerceAtLeast(0)
                val existingLbRating = (prevLb?.rating ?: 1000L).toInt().coerceIn(800, 5000)
                val lbWins = min(existingLbWins + 1, nextWins).coerceAtLeast(existingLbWins)
                val lbLosses = min(existingLbLosses + 1, nextLosses).coerceAtLeast(existingLbLosses)
                val lbMatches = (lbWins + lbLosses).coerceAtMost(200000)
                val lbRating = nextRating.coerceAtMost(existingLbRating + 30).coerceIn(800, 5000)

                lbRef.update(
                    mapOf(
                        "displayName" to displayName,
                        "characterId" to input.playerCharacter.name,
                        "wins" to lbWins,
                        "losses" to lbLosses,
                        "matchesPlayed" to lbMatches,
                        "pointsScored" to nextPoints,
                        "bestRally" to nextBestRally,
                        "rating" to lbRating,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                val initWins = if (won) 1 else 0
                val initLosses = if (won) 0 else 1
                val initRating = nextRating.coerceIn(980, 1030)
                lbRef.set(
                    mapOf(
                        "uid" to signedInUid,
                        "displayName" to displayName,
                        "characterId" to input.playerCharacter.name,
                        "wins" to initWins,
                        "losses" to initLosses,
                        "matchesPlayed" to (initWins + initLosses),
                        "pointsScored" to input.scoreLower.coerceIn(0, 5000000),
                        "bestRally" to input.bestRally.coerceIn(0, 999),
                        "rating" to initRating,
                        "isPublic" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }

            Result.success(RecordMatchOutcome(savedToCloud = true, matchId = safeMatchId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, matchPath)
            localPrefs.appendLocalMatch(safeMatchId, input, ratingDelta)
            Result.success(
                RecordMatchOutcome(
                    savedToCloud = false,
                    matchId = safeMatchId,
                    errorMessage = unwrapFirestoreMessage(e, "Saved locally instead."),
                ),
            )
        }
    }

    suspend fun fetchMatchHistory(user: FirebaseUser?): Result<List<MatchHistoryItem>> {
        if (user == null || user.uid.isBlank()) {
            return Result.success(localPrefs.getLocalHistory())
        }
        val uid = user.uid
        val path = "matches"
        return try {
            val snap = db.collection(path)
                .whereArrayContains("participants", uid)
                .limit(25)
                .get()
                .await()

            val items = snap.toObjects(
                MatchRecordDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ).map { doc ->
                val createdMs = doc.createdAt?.toDate()?.time ?: 0L
                val winnerSide = if (doc.winnerSide == CourtSide.UPPER.name) {
                    CourtSide.UPPER
                } else {
                    CourtSide.LOWER
                }
                MatchHistoryItem(
                    matchId = doc.matchId.ifBlank { doc.id },
                    roomCode = doc.roomCode,
                    mode = MatchRecordMode.fromStringOrDefault(doc.mode, MatchRecordMode.SOLO_1V1),
                    scoringMode = if (doc.scoringMode == ScoringMode.SIDE_OUT.name) {
                        ScoringMode.SIDE_OUT
                    } else {
                        ScoringMode.RALLY
                    },
                    playerCharacter = CharacterId.fromStringOrDefault(doc.playerCharacter, CharacterId.RIO),
                    partnerCharacter = doc.partnerCharacter?.let {
                        CharacterId.fromStringOrDefault(it, CharacterId.DILL)
                    },
                    opponentCharacter = CharacterId.fromStringOrDefault(doc.opponentCharacter, CharacterId.LOLA),
                    winnerSide = winnerSide,
                    scoreLower = doc.scoreLower.toInt().coerceIn(0, 99),
                    scoreUpper = doc.scoreUpper.toInt().coerceIn(0, 99),
                    bestRally = doc.bestRally.toInt().coerceIn(0, 999),
                    validated = doc.validated,
                    createdAtMs = createdMs,
                    source = HistorySource.CLOUD,
                )
            }.sortedByDescending { it.createdAtMs }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun fetchLeaderboard(
        user: FirebaseUser?,
        fallbackCharacter: CharacterId,
    ): Result<List<LeaderboardRow>> {
        if (user == null || user.uid.isBlank()) {
            val local = localPrefs.getLocalProfile(fallbackCharacter)
            return Result.success(
                listOf(
                    LeaderboardRow(
                        uid = local.uid,
                        displayName = local.displayName,
                        characterId = local.characterId,
                        wins = local.wins,
                        losses = local.losses,
                        matchesPlayed = local.matchesPlayed,
                        bestRally = local.bestRally,
                        rating = local.rating,
                    ),
                ),
            )
        }
        val path = "leaderboard"
        return try {
            val snap = db.collection(path)
                .whereEqualTo("isPublic", true)
                .limit(50)
                .get()
                .await()

            val rows = snap.toObjects(
                LeaderboardDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ).map { doc ->
                LeaderboardRow(
                    uid = doc.uid.ifBlank { doc.id },
                    displayName = doc.displayName.ifBlank { "Player" },
                    characterId = CharacterId.fromStringOrDefault(doc.characterId, CharacterId.RIO),
                    wins = doc.wins.toInt().coerceAtLeast(0),
                    losses = doc.losses.toInt().coerceAtLeast(0),
                    matchesPlayed = doc.matchesPlayed.toInt().coerceAtLeast(0),
                    bestRally = doc.bestRally.toInt().coerceAtLeast(0),
                    rating = doc.rating.toInt().coerceIn(800, 5000),
                )
            }.sortedWith(
                compareByDescending<LeaderboardRow> { it.rating }
                    .thenByDescending { it.wins },
            )
            Result.success(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}
