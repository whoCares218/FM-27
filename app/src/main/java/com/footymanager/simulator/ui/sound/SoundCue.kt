package com.footymanager.simulator.ui.sound

/** The cues the game uses, kept separate from the audio backend. */
enum class SoundCue {
    /** A soft tap for navigation and ordinary buttons. */
    CLICK,

    /** A slightly stronger click for confirmations. */
    CONFIRM,

    /** A gentle downward tap for cancels and dismissals. */
    CANCEL,

    /** A satisfying chime for successful actions. */
    SUCCESS,

    /** A low buzz for rejected or failed actions. */
    FAILURE,

    /** A rising tone for a goal. */
    GOAL,

    /** A referee whistle for kick-off, half time and full time. */
    WHISTLE,

    /** A bright chirp for a completed transfer. */
    TRANSFER,

    /** A short, neutral select sound for dropdowns and pickers. */
    SELECT,

    /** A soft warning tone for risky or costly actions. */
    WARNING
}
