package com.footymanager.simulator.ui.sound

/**
 * The five original background-music moods. Each is synthesised on-device from
 * its own chord progression, tempo and timbre, so there is nothing to licence and
 * the APK ships no audio assets.
 */
enum class MusicTrack(
    val label: String,
    val description: String
) {
    CALM_MANAGER("Calm Manager", "Relaxed, strategic, low intensity"),
    MATCHDAY_ENERGY("Matchday Energy", "Energetic, football atmosphere"),
    MODERN_SPORTS("Modern Sports", "Modern, professional, rhythmic"),
    EUROPEAN_NIGHT("European Night", "Dramatic, premium, atmospheric"),
    VICTORY_MOTIVATION("Victory Motivation", "Uplifting, positive, energetic")
}

/** How the player advances between tracks. */
enum class MusicPlayMode(val label: String) {
    SELECTED("Selected track"),
    SHUFFLE("Shuffle")
}
