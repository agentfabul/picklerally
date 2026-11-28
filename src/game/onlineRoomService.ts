import { doc, getDoc, onSnapshot, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { onDisconnect, onValue, ref, set } from 'firebase/database';
import type { CharacterId } from './ballPhysics';
import {
  auth,
  db,
  ensureUserProfile,
  OperationType,
  handleFirestoreError,
  rtdb,
  signInPlayerAnonymously,
} from './firebaseClient';
import {
  RoomCodeGenerator,
  type MatchStateSnapshot,
  type PlayerInputSnapshot,
  type RoomStatus,
} from './onlineMatch1v1';

export interface ActiveOnlineRoom {
  roomCode: string;
  isHost: boolean;
  localUid: string;
  transport: 'RTDB' | 'LOCAL_TAB_SYNC';
  isNearby: boolean;
  firestoreSynced: boolean;
  opponentConnected: boolean;
  /** Profile name + hero shared with the other player (Nearby / Online). */
  localName?: string;
  localCharacter?: CharacterId;
  opponentName?: string;
  opponentCharacter?: CharacterId;
  status: RoomStatus;
}

type ChannelPacket =
  | { type: 'JOIN_REQUEST'; roomCode: string; guestUid: string; name?: string; characterId?: CharacterId }
  | { type: 'META'; roomCode: string; hostUid: string; status: RoomStatus; name?: string; characterId?: CharacterId }
  | { type: 'INPUT'; roomCode: string; input: PlayerInputSnapshot }
  | { type: 'STATE'; roomCode: string; snapshot: MatchStateSnapshot };

export interface RoomSessionCallbacks {
  onOpponentJoined: (guestUid: string) => void;
  onRemoteInput: (input: PlayerInputSnapshot) => void;
  onHostSnapshot: (snapshot: MatchStateSnapshot) => void;
  onRoomStatusChange: (status: RoomStatus) => void;
}

/**
 * Coordinates 1v1 multiplayer room creation, joining, live inputs, and 20 Hz state sync.
 * Uses Firebase Realtime Database when databaseURL is configured in firebase-applet-config.json,
 * and seamlessly syncs two browser tabs on the same origin via BroadcastChannel + Firestore room directory.
 */
export class OnlineRoomSession {
  readonly room: ActiveOnlineRoom;
  private channel: BroadcastChannel | null = null;
  private unsubscribers: (() => void)[] = [];
  private disposed = false;

  private constructor(room: ActiveOnlineRoom) {
    this.room = room;
  }

  static async createRoom(
    callbacks: RoomSessionCallbacks,
    characterId: CharacterId = 'RIO',
  ): Promise<OnlineRoomSession> {
    let uid = auth.currentUser?.uid ?? `local-host-${Math.floor(1000 + Math.random() * 9000)}`;
    let firestoreSynced = false;

    if (auth.currentUser) {
      uid = auth.currentUser.uid;
      try {
        await ensureUserProfile(auth.currentUser, characterId);
      } catch {
        // Profile creation failure surfaced only if rules reject
      }
    } else {
      try {
        const user = await signInPlayerAnonymously();
        uid = user.uid;
      } catch {
        // Anonymous auth may not be enabled yet in Console; local tab channel still works cleanly
      }
    }

    const roomCode = RoomCodeGenerator.generate();

    if (auth.currentUser) {
      const fsPath = `rooms/${roomCode}`;
      try {
        await setDoc(doc(db, 'rooms', roomCode), {
          roomCode,
          hostUid: uid,
          mode: 'SINGLES_1V1',
          status: 'WAITING',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
        firestoreSynced = true;
      } catch (err) {
        if (err instanceof Error && err.message.includes('Missing or insufficient permissions')) {
          handleFirestoreError(err, OperationType.CREATE, fsPath);
        }
      }
    }

    const transport: ActiveOnlineRoom['transport'] = rtdb ? 'RTDB' : 'LOCAL_TAB_SYNC';
    const session = new OnlineRoomSession({
      roomCode,
      isHost: true,
      localUid: uid,
      transport,
      isNearby: false,
      firestoreSynced,
      opponentConnected: false,
      status: 'WAITING',
    });

    session.attachTransport(callbacks);

    if (rtdb) {
      const metaRef = ref(rtdb, `rooms/${roomCode}/meta`);
      await set(metaRef, {
        host: uid,
        mode: 'SINGLES_1V1',
        status: 'WAITING',
        createdAt: Date.now(),
      });
      await onDisconnect(ref(rtdb, `rooms/${roomCode}/meta/status`)).set('HOST_LEFT');
      await set(ref(rtdb, `rooms/${roomCode}/players/${uid}`), {
        uid,
        team: 0,
        characterId,
        connected: true,
      });
      await onDisconnect(ref(rtdb, `rooms/${roomCode}/players/${uid}/connected`)).set(false);
    } else {
      session.persistLocalRoomMeta(uid, 'WAITING');
    }

    return session;
  }

  static async joinRoom(
    rawCode: string,
    callbacks: RoomSessionCallbacks,
    characterId: CharacterId = 'LOLA',
  ): Promise<OnlineRoomSession> {
    const roomCode = rawCode.trim().toUpperCase();
    if (!RoomCodeGenerator.isValid(roomCode)) {
      throw new Error('Enter a valid room code like PKL-482');
    }

    let uid = auth.currentUser?.uid ?? `local-guest-${Math.floor(1000 + Math.random() * 9000)}`;
    let firestoreSynced = false;

    if (!auth.currentUser) {
      try {
        const user = await signInPlayerAnonymously();
        uid = user.uid;
      } catch {
        // Fallback UID for local BroadcastChannel tab testing
      }
    } else {
      uid = auth.currentUser.uid;
    }

    if (auth.currentUser) {
      try {
        const roomSnap = await getDoc(doc(db, 'rooms', roomCode));
        if (roomSnap.exists()) {
          const data = roomSnap.data();
          if (data.status === 'WAITING' && data.hostUid !== uid) {
            await updateDoc(doc(db, 'rooms', roomCode), {
              guestUid: uid,
              status: 'PLAYING',
              updatedAt: serverTimestamp(),
            });
          }
          firestoreSynced = true;
        }
      } catch (err) {
        if (err instanceof Error && err.message.includes('Missing or insufficient permissions')) {
          handleFirestoreError(err, OperationType.UPDATE, `rooms/${roomCode}`);
        }
      }
    }

    const transport: ActiveOnlineRoom['transport'] = rtdb ? 'RTDB' : 'LOCAL_TAB_SYNC';
    const session = new OnlineRoomSession({
      roomCode,
      isHost: false,
      localUid: uid,
      transport,
      isNearby: false,
      firestoreSynced,
      opponentConnected: true,
      status: 'PLAYING',
    });

    session.attachTransport(callbacks);

    if (rtdb) {
      await set(ref(rtdb, `rooms/${roomCode}/players/${uid}`), {
        uid,
        team: 1,
        characterId,
        connected: true,
      });
      await onDisconnect(ref(rtdb, `rooms/${roomCode}/players/${uid}/connected`)).set(false);
    } else {
      session.postLocalPacket({ type: 'JOIN_REQUEST', roomCode, guestUid: uid });
    }

    return session;
  }

  /** Nearby / local play: no sign-in, no Firebase. Uses the local channel only. */
  static async createLocalRoom(
    callbacks: RoomSessionCallbacks,
    characterId: CharacterId = 'RIO',
    displayName?: string,
  ): Promise<OnlineRoomSession> {
    const localProfile = { localName: displayName?.trim() || undefined, localCharacter: characterId };
    const session = new OnlineRoomSession({
      roomCode: RoomCodeGenerator.generate(),
      isHost: true,
      localUid: `nearby-host-${Math.floor(1000 + Math.random() * 9000)}`,
      transport: 'LOCAL_TAB_SYNC',
      isNearby: true,
      firestoreSynced: false,
      opponentConnected: false,
      status: 'WAITING',
    });
    Object.assign(session.room, localProfile);
    session.attachTransport(callbacks);
    return session;
  }

  static async joinLocalRoom(
    rawCode: string,
    callbacks: RoomSessionCallbacks,
    characterId: CharacterId = 'LOLA',
    displayName?: string,
  ): Promise<OnlineRoomSession> {
    const localProfile = { localName: displayName?.trim() || undefined, localCharacter: characterId };
    const roomCode = rawCode.trim().toUpperCase();
    if (!RoomCodeGenerator.isValid(roomCode)) {
      throw new Error('That nearby game is no longer available');
    }
    const uid = `nearby-guest-${Math.floor(1000 + Math.random() * 9000)}`;
    const session = new OnlineRoomSession({
      roomCode,
      isHost: false,
      localUid: uid,
      transport: 'LOCAL_TAB_SYNC',
      isNearby: true,
      firestoreSynced: false,
      opponentConnected: true,
      status: 'PLAYING',
    });
    Object.assign(session.room, localProfile);
    session.attachTransport(callbacks);
    session.postLocalPacket({ type: 'JOIN_REQUEST', roomCode, guestUid: uid, name: session.room.localName, characterId });
    return session;
  }

  private attachTransport(callbacks: RoomSessionCallbacks) {
    if (rtdb && this.room.transport === 'RTDB') {
      const playersUnsub = onValue(ref(rtdb, `rooms/${this.room.roomCode}/players`), (snap) => {
        const val = snap.val() as Record<string, { uid: string; connected: boolean }> | null;
        if (!val) return;
        const guest = Object.values(val).find((p) => p.uid !== this.room.localUid && p.connected);
        if (guest && !this.room.opponentConnected) {
          this.room.opponentConnected = true;
          this.room.status = 'PLAYING';
          callbacks.onOpponentJoined(guest.uid);
        }
      });

      const metaUnsub = onValue(ref(rtdb, `rooms/${this.room.roomCode}/meta/status`), (snap) => {
        const status = snap.val() as RoomStatus | null;
        if (status) {
          this.room.status = status;
          callbacks.onRoomStatusChange(status);
        }
      });

      const inputsUnsub = onValue(ref(rtdb, `rooms/${this.room.roomCode}/inputs`), (snap) => {
        if (!this.room.isHost) return;
        const val = snap.val() as Record<string, PlayerInputSnapshot> | null;
        if (!val) return;
        for (const inp of Object.values(val)) {
          if (inp.uid !== this.room.localUid) {
            callbacks.onRemoteInput(inp);
          }
        }
      });

      const stateUnsub = onValue(ref(rtdb, `rooms/${this.room.roomCode}/state`), (snap) => {
        if (this.room.isHost) return;
        const state = snap.val() as MatchStateSnapshot | null;
        if (state) {
          callbacks.onHostSnapshot(state);
        }
      });

      this.unsubscribers.push(playersUnsub, metaUnsub, inputsUnsub, stateUnsub);
    } else if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('pickle-rally-rooms-v1');
      this.channel = channel;
      channel.onmessage = (ev: MessageEvent<ChannelPacket>) => {
        const msg = ev.data;
        if (!msg || msg.roomCode !== this.room.roomCode) return;

        if (msg.type === 'JOIN_REQUEST' && this.room.isHost) {
          this.room.opponentConnected = true;
          this.room.status = 'PLAYING';
          if (msg.name) this.room.opponentName = msg.name;
          if (msg.characterId) this.room.opponentCharacter = msg.characterId;
          callbacks.onOpponentJoined(msg.guestUid);
          this.postLocalPacket({
            type: 'META',
            roomCode: this.room.roomCode,
            hostUid: this.room.localUid,
            name: this.room.localName,
            characterId: this.room.localCharacter,
            status: 'PLAYING',
          });
        } else if (msg.type === 'META') {
          if (msg.name) this.room.opponentName = msg.name;
          if (msg.characterId) this.room.opponentCharacter = msg.characterId;
          this.room.status = msg.status;
          callbacks.onRoomStatusChange(msg.status);
        } else if (msg.type === 'INPUT' && this.room.isHost && msg.input.uid !== this.room.localUid) {
          callbacks.onRemoteInput(msg.input);
        } else if (msg.type === 'STATE' && !this.room.isHost) {
          callbacks.onHostSnapshot(msg.snapshot);
        }
      };
    }

    if (auth.currentUser) {
      const fsUnsub = onSnapshot(
        doc(db, 'rooms', this.room.roomCode),
        (snap) => {
          if (!snap.exists()) return;
          const data = snap.data();
          if (data.status && data.status !== this.room.status) {
            this.room.status = data.status as RoomStatus;
            callbacks.onRoomStatusChange(data.status as RoomStatus);
          }
          if (this.room.isHost && data.guestUid && !this.room.opponentConnected) {
            this.room.opponentConnected = true;
            callbacks.onOpponentJoined(String(data.guestUid));
          }
        },
        () => {
          // Ignore snapshot teardown errors cleanly
        },
      );
      this.unsubscribers.push(fsUnsub);
    }
  }

  sendInput(input: PlayerInputSnapshot) {
    if (this.disposed) return;
    if (rtdb && this.room.transport === 'RTDB') {
      set(ref(rtdb, `rooms/${this.room.roomCode}/inputs/${input.uid}`), input).catch(() => {});
    } else {
      this.postLocalPacket({ type: 'INPUT', roomCode: this.room.roomCode, input });
    }
  }

  publishState(snapshot: MatchStateSnapshot) {
    if (this.disposed || !this.room.isHost) return;
    if (rtdb && this.room.transport === 'RTDB') {
      set(ref(rtdb, `rooms/${this.room.roomCode}/state`), snapshot).catch(() => {});
    } else {
      this.postLocalPacket({ type: 'STATE', roomCode: this.room.roomCode, snapshot });
    }
  }

  async leave() {
    if (this.disposed) return;
    this.disposed = true;
    const nextStatus: RoomStatus = this.room.isHost ? 'HOST_LEFT' : this.room.status;

    if (rtdb && this.room.transport === 'RTDB') {
      if (this.room.isHost) {
        set(ref(rtdb, `rooms/${this.room.roomCode}/meta/status`), 'HOST_LEFT').catch(() => {});
      }
      set(ref(rtdb, `rooms/${this.room.roomCode}/players/${this.room.localUid}/connected`), false).catch(() => {});
    } else if (this.room.isHost) {
      this.postLocalPacket({
        type: 'META',
        roomCode: this.room.roomCode,
        hostUid: this.room.localUid,
        status: nextStatus,
      });
    }

    if (auth.currentUser && this.room.isHost && this.room.firestoreSynced) {
      try {
        await updateDoc(doc(db, 'rooms', this.room.roomCode), {
          status: 'HOST_LEFT',
          updatedAt: serverTimestamp(),
        });
      } catch {
        // Ignore cleanup failure on exit
      }
    }

    this.unsubscribers.forEach((unsub) => unsub());
    this.unsubscribers = [];
    if (this.channel) {
      this.channel.close();
      this.channel = null;
    }
  }

  private postLocalPacket(packet: ChannelPacket) {
    this.channel?.postMessage(packet);
  }

  private persistLocalRoomMeta(hostUid: string, status: RoomStatus) {
    this.postLocalPacket({
      type: 'META',
      roomCode: this.room.roomCode,
      hostUid,
      status,
    });
  }
}


/** A host found by "Find Players" in Nearby mode. */
export interface NearbyHostInfo {
  roomCode: string;
  hostName: string;
  characterId: CharacterId;
  lastSeen: number;
}

type NearbyPacket =
  | { type: 'ANNOUNCE'; roomCode: string; hostName: string; characterId: CharacterId }
  | { type: 'GONE'; roomCode: string }
  | { type: 'PROBE' };

/**
 * Nearby lobby for the web preview: hosts announce themselves every second and finders keep a
 * live list. In the browser this reaches other tabs of the same browser; the Android app uses
 * Google Nearby Connections (Wi-Fi / hotspot / Bluetooth) for the same flow.
 */
export class NearbyLobby {
  static readonly CHANNEL = 'pickle-rally-nearby-v1';
  static readonly STALE_MS = 3500;

  /** Start announcing a hosted game. Returns a function that stops announcing. */
  static announce(roomCode: string, hostName: string, characterId: CharacterId): () => void {
    if (typeof BroadcastChannel === 'undefined') return () => {};
    const channel = new BroadcastChannel(NearbyLobby.CHANNEL);
    const send = () => {
      const packet: NearbyPacket = { type: 'ANNOUNCE', roomCode, hostName, characterId };
      channel.postMessage(packet);
    };
    channel.onmessage = (ev: MessageEvent<NearbyPacket>) => {
      if (ev.data?.type === 'PROBE') send();
    };
    send();
    const timer = setInterval(send, 1000);
    return () => {
      clearInterval(timer);
      const gone: NearbyPacket = { type: 'GONE', roomCode };
      channel.postMessage(gone);
      channel.close();
    };
  }

  /** Search for hosted games. Calls onChange with the current list. Returns a stop function. */
  static discover(onChange: (hosts: NearbyHostInfo[]) => void): () => void {
    if (typeof BroadcastChannel === 'undefined') {
      onChange([]);
      return () => {};
    }
    const channel = new BroadcastChannel(NearbyLobby.CHANNEL);
    const hosts = new Map<string, NearbyHostInfo>();
    const emit = () =>
      onChange([...hosts.values()].sort((a, b) => a.hostName.localeCompare(b.hostName)));
    channel.onmessage = (ev: MessageEvent<NearbyPacket>) => {
      const p = ev.data;
      if (!p) return;
      if (p.type === 'ANNOUNCE') {
        const isNew = !hosts.has(p.roomCode);
        hosts.set(p.roomCode, { ...p, lastSeen: Date.now() });
        if (isNew) emit();
      } else if (p.type === 'GONE') {
        if (hosts.delete(p.roomCode)) emit();
      }
    };
    const probe: NearbyPacket = { type: 'PROBE' };
    channel.postMessage(probe);
    const prune = setInterval(() => {
      const now = Date.now();
      let changed = false;
      for (const [code, info] of hosts) {
        if (now - info.lastSeen > NearbyLobby.STALE_MS) {
          hosts.delete(code);
          changed = true;
        }
      }
      if (changed) emit();
    }, 1000);
    emit();
    return () => {
      clearInterval(prune);
      channel.close();
    };
  }
}
