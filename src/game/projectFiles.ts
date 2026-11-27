import settingsGradle from '../../settings.gradle.kts?raw';
import rootBuildGradle from '../../build.gradle.kts?raw';
import gradleProperties from '../../gradle.properties?raw';
import databaseRulesJson from '../../database.rules.json?raw';
import firestoreRules from '../../firestore.rules?raw';
import firebaseBlueprintJson from '../../firebase-blueprint.json?raw';
import coreBuildGradle from '../../core/build.gradle.kts?raw';
import pickleRallyGameKt from '../../core/src/main/kotlin/com/picklerally/core/PickleRallyGame.kt?raw';
import courtSpecKt from '../../core/src/main/kotlin/com/picklerally/core/court/CourtSpec.kt?raw';
import courtColorsKt from '../../core/src/main/kotlin/com/picklerally/core/court/CourtColors.kt?raw';
import characterCatalogKt from '../../core/src/main/kotlin/com/picklerally/core/characters/CharacterCatalog.kt?raw';
import characterAssetLoaderKt from '../../core/src/main/kotlin/com/picklerally/core/characters/CharacterAssetLoader.kt?raw';
import soundServiceKt from '../../core/src/main/kotlin/com/picklerally/core/audio/SoundService.kt?raw';
import ballPhysicsKt from '../../core/src/main/kotlin/com/picklerally/core/physics/BallPhysics.kt?raw';
import rulesEngineKt from '../../core/src/main/kotlin/com/picklerally/core/rules/RulesEngine.kt?raw';
import singlePlayerMatchKt from '../../core/src/main/kotlin/com/picklerally/core/sim/SinglePlayerMatch.kt?raw';
import doublesMatchKt from '../../core/src/main/kotlin/com/picklerally/core/sim/DoublesMatch.kt?raw';
import onlineMatch1v1Kt from '../../core/src/main/kotlin/com/picklerally/core/sim/OnlineMatch1v1.kt?raw';
import networkServiceKt from '../../core/src/main/kotlin/com/picklerally/core/net/NetworkService.kt?raw';
import statsModelsKt from '../../core/src/main/kotlin/com/picklerally/core/net/StatsModels.kt?raw';
import mainMenuScreenKt from '../../core/src/main/kotlin/com/picklerally/core/screens/MainMenuScreen.kt?raw';
import characterSelectScreenKt from '../../core/src/main/kotlin/com/picklerally/core/screens/CharacterSelectScreen.kt?raw';
import courtScreenKt from '../../core/src/main/kotlin/com/picklerally/core/screens/CourtScreen.kt?raw';
import courtSpecTestKt from '../../core/src/test/kotlin/com/picklerally/core/court/CourtSpecTest.kt?raw';
import ballPhysicsTestKt from '../../core/src/test/kotlin/com/picklerally/core/physics/BallPhysicsTest.kt?raw';
import rulesEngineTestKt from '../../core/src/test/kotlin/com/picklerally/core/rules/RulesEngineTest.kt?raw';
import onlineMatch1v1TestKt from '../../core/src/test/kotlin/com/picklerally/core/net/OnlineMatch1v1Test.kt?raw';
import doublesMatchTestKt from '../../core/src/test/kotlin/com/picklerally/core/sim/DoublesMatchTest.kt?raw';
import phase5PolishTestKt from '../../core/src/test/kotlin/com/picklerally/core/characters/Phase5PolishTest.kt?raw';
import androidBuildGradle from '../../android/build.gradle.kts?raw';
import androidManifestXml from '../../android/src/main/AndroidManifest.xml?raw';
import androidLauncherKt from '../../android/src/main/kotlin/com/picklerally/android/AndroidLauncher.kt?raw';
import firebaseNetworkKt from '../../android/src/main/kotlin/com/picklerally/android/FirebaseNetworkService.kt?raw';
import localStubNetworkKt from '../../android/src/main/kotlin/com/picklerally/android/LocalStubNetworkService.kt?raw';
import desktopBuildGradle from '../../desktop/build.gradle.kts?raw';
import desktopLauncherKt from '../../desktop/src/main/kotlin/com/picklerally/desktop/DesktopLauncher.kt?raw';
import fakeLocalNetworkKt from '../../desktop/src/main/kotlin/com/picklerally/desktop/FakeLocalNetworkService.kt?raw';
import functionsIndexTs from '../../functions/src/index.ts?raw';
import matchValidatorTs from '../../functions/src/matchValidator.ts?raw';

export type ModuleGroup = 'root' | 'core' | 'android' | 'desktop' | 'functions';

export interface ProjectFileEntry {
  path: string;
  name: string;
  module: ModuleGroup;
  language: 'kotlin' | 'xml' | 'typescript' | 'properties' | 'json';
  summary: string;
  content: string;
}

export const PROJECT_FILES: ProjectFileEntry[] = [
  {
    path: 'core/src/main/kotlin/com/picklerally/core/sim/DoublesMatch.kt',
    name: 'DoublesMatch.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Phase 6 2v2 Doubles simulation with 0-0-2 serving call, side switching, AI partner & opponents',
    content: doublesMatchKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/net/StatsModels.kt',
    name: 'StatsModels.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Pure Kotlin profile, match history, leaderboard models & client-side score validator',
    content: statsModelsKt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/sim/DoublesMatchTest.kt',
    name: 'DoublesMatchTest.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'JUnit 5 tests for 2v2 Doubles 0-0-2 rotation, side-switching & MatchResultValidator',
    content: doublesMatchTestKt,
  },
  {
    path: 'functions/src/index.ts',
    name: 'functions/src/index.ts',
    module: 'functions',
    language: 'typescript',
    summary: 'Cloud Functions v2 (validateMatchOnCreate & submitOnlineMatchResult) verifying results & stats',
    content: functionsIndexTs,
  },
  {
    path: 'functions/src/matchValidator.ts',
    name: 'functions/src/matchValidator.ts',
    module: 'functions',
    language: 'typescript',
    summary: 'Pure score, participant & ELO rating validation shared between Cloud Functions & client checks',
    content: matchValidatorTs,
  },
  {
    path: 'firestore.rules',
    name: 'firestore.rules',
    module: 'root',
    language: 'properties',
    summary: 'Deployed Firestore rules for users/{uid}, rooms/{roomCode}, matches/{matchId} & leaderboard/{uid}',
    content: firestoreRules,
  },
  {
    path: 'firebase-blueprint.json',
    name: 'firebase-blueprint.json',
    module: 'root',
    language: 'json',
    summary: 'Firestore collection blueprint for UserProfile, RoomDirectoryEntry, MatchRecord & LeaderboardEntry',
    content: firebaseBlueprintJson,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/screens/MainMenuScreen.kt',
    name: 'MainMenuScreen.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Portrait main menu with 1v1 Solo, 2v2 Doubles, Online 1v1, Character Select & Scoring controls',
    content: mainMenuScreenKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/screens/CharacterSelectScreen.kt',
    name: 'CharacterSelectScreen.kt',
    module: 'core',
    language: 'kotlin',
    summary: '2x2 character roster selector for Rio, Lola, Bert & unlockable Dill',
    content: characterSelectScreenKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/characters/CharacterAssetLoader.kt',
    name: 'CharacterAssetLoader.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'libGDX TextureAtlas loader for 48x48 court animations & 64x64 menu portraits with shape fallback',
    content: characterAssetLoaderKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/audio/SoundService.kt',
    name: 'SoundService.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Procedural PCM AudioDevice sound synthesizer for hits, bounces, faults, points & victory',
    content: soundServiceKt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/characters/Phase5PolishTest.kt',
    name: 'Phase5PolishTest.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'JUnit 5 tests for cosmetic parity, unlock flags, 48x48/64x64 sprite sheet specs & callouts',
    content: phase5PolishTestKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/sim/OnlineMatch1v1.kt',
    name: 'OnlineMatch1v1.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Host-authoritative 1v1 simulation with 20 Hz snapshots, guest perspective mirroring & interpolation',
    content: onlineMatch1v1Kt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/net/OnlineMatch1v1Test.kt',
    name: 'OnlineMatch1v1Test.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'JUnit 5 tests for PKL-### room codes, mirroring, 20 Hz snapshot emission & host disconnect',
    content: onlineMatch1v1TestKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/net/NetworkService.kt',
    name: 'NetworkService.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Pure Kotlin multiplayer contract with 1v1 & 2v2 doubles snapshot fields',
    content: networkServiceKt,
  },
  {
    path: 'android/src/main/kotlin/com/picklerally/android/FirebaseNetworkService.kt',
    name: 'FirebaseNetworkService.kt',
    module: 'android',
    language: 'kotlin',
    summary: 'Android Firebase Auth & RTDB implementation with 2v2 team balancing & onDisconnect() handlers',
    content: firebaseNetworkKt,
  },
  {
    path: 'database.rules.json',
    name: 'database.rules.json',
    module: 'root',
    language: 'json',
    summary: 'Firebase RTDB rules enforcing player-only input writes and host-only state/meta writes',
    content: databaseRulesJson,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/screens/CourtScreen.kt',
    name: 'CourtScreen.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Portrait court renderer wired to selected characters, sound cues & animated fault banners',
    content: courtScreenKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/rules/RulesEngine.kt',
    name: 'RulesEngine.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Official pickleball rules & scoring engine (two-bounce, kitchen NVZ, diagonal serve, rally/side-out)',
    content: rulesEngineKt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/rules/RulesEngineTest.kt',
    name: 'RulesEngineTest.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'JUnit 5 unit tests for every pickleball rule, fault, and singles/doubles scoring scenario',
    content: rulesEngineTestKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/sim/SinglePlayerMatch.kt',
    name: 'SinglePlayerMatch.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Single-player loop wired to RulesEngine scoring and fault enforcement',
    content: singlePlayerMatchKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/physics/BallPhysics.kt',
    name: 'BallPhysics.kt',
    module: 'core',
    language: 'kotlin',
    summary: '60 Hz 3D ball physics (x, y, z), gravity, bounce restitution, net check & swipe classifier',
    content: ballPhysicsKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/characters/CharacterCatalog.kt',
    name: 'CharacterCatalog.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Cosmetic character color palettes (Rio, Lola, Bert, Dill) for shape rendering',
    content: characterCatalogKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/court/CourtSpec.kt',
    name: 'CourtSpec.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Official USA Pickleball 20x44 ft geometry, 7 ft kitchen, net sag, and zone classifier',
    content: courtSpecKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/court/CourtColors.kt',
    name: 'CourtColors.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Palette constants harmonized with Pickle Rally pixel-art branding',
    content: courtColorsKt,
  },
  {
    path: 'core/src/main/kotlin/com/picklerally/core/PickleRallyGame.kt',
    name: 'PickleRallyGame.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'KtxGame entry point passing NetworkService to CourtScreen',
    content: pickleRallyGameKt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/physics/BallPhysicsTest.kt',
    name: 'BallPhysicsTest.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Pure JVM JUnit 5 tests for 3D gravity, bounce, net check & swipe classification',
    content: ballPhysicsTestKt,
  },
  {
    path: 'core/src/test/kotlin/com/picklerally/core/court/CourtSpecTest.kt',
    name: 'CourtSpecTest.kt',
    module: 'core',
    language: 'kotlin',
    summary: 'Pure JVM JUnit 5 tests for court dimensions and boundary checks',
    content: courtSpecTestKt,
  },
  {
    path: 'core/build.gradle.kts',
    name: 'core/build.gradle.kts',
    module: 'core',
    language: 'kotlin',
    summary: 'Pure Kotlin module dependencies (libGDX + KTX + JUnit 5)',
    content: coreBuildGradle,
  },
  {
    path: 'android/src/main/kotlin/com/picklerally/android/AndroidLauncher.kt',
    name: 'AndroidLauncher.kt',
    module: 'android',
    language: 'kotlin',
    summary: 'AndroidApplication launcher wired to FirebaseNetworkService',
    content: androidLauncherKt,
  },
  {
    path: 'android/src/main/kotlin/com/picklerally/android/LocalStubNetworkService.kt',
    name: 'LocalStubNetworkService.kt',
    module: 'android',
    language: 'kotlin',
    summary: 'Offline NetworkService fallback for instrumented or local Android runs',
    content: localStubNetworkKt,
  },
  {
    path: 'android/src/main/AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    module: 'android',
    language: 'xml',
    summary: 'Portrait orientation lock plus INTERNET and ACCESS_NETWORK_STATE permissions',
    content: androidManifestXml,
  },
  {
    path: 'android/build.gradle.kts',
    name: 'android/build.gradle.kts',
    module: 'android',
    language: 'kotlin',
    summary: 'Android Gradle config with Firebase BoM (Auth + RTDB), coroutines-play-services & Google Services plugin',
    content: androidBuildGradle,
  },
  {
    path: 'desktop/src/main/kotlin/com/picklerally/desktop/DesktopLauncher.kt',
    name: 'DesktopLauncher.kt',
    module: 'desktop',
    language: 'kotlin',
    summary: 'LWJGL3 450x900 portrait window simulating touch with mouse',
    content: desktopLauncherKt,
  },
  {
    path: 'desktop/src/main/kotlin/com/picklerally/desktop/FakeLocalNetworkService.kt',
    name: 'FakeLocalNetworkService.kt',
    module: 'desktop',
    language: 'kotlin',
    summary: 'Shared in-memory multi-client room hub for local desktop 1v1 testing without Firebase',
    content: fakeLocalNetworkKt,
  },
  {
    path: 'desktop/build.gradle.kts',
    name: 'desktop/build.gradle.kts',
    module: 'desktop',
    language: 'kotlin',
    summary: 'Desktop LWJGL3 module Gradle script',
    content: desktopBuildGradle,
  },
  {
    path: 'settings.gradle.kts',
    name: 'settings.gradle.kts',
    module: 'root',
    language: 'kotlin',
    summary: 'Multi-module Gradle settings including :core, :android, :desktop',
    content: settingsGradle,
  },
  {
    path: 'build.gradle.kts',
    name: 'build.gradle.kts',
    module: 'root',
    language: 'kotlin',
    summary: 'Root Gradle Kotlin DSL plugin declarations including Google Services',
    content: rootBuildGradle,
  },
  {
    path: 'gradle.properties',
    name: 'gradle.properties',
    module: 'root',
    language: 'properties',
    summary: 'Pinned libGDX 1.12.1, KTX 1.12.1-rc1, Kotlin 2.0.21, AGP 8.5.2, Firebase BoM 33.7.0',
    content: gradleProperties,
  },
];
