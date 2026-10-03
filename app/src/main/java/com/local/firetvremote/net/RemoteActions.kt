package com.local.firetvremote.net

object RemoteActions {
    const val UP = "dpad_up"
    const val DOWN = "dpad_down"
    const val LEFT = "dpad_left"
    const val RIGHT = "dpad_right"
    const val SELECT = "select"
    const val HOME = "home"
    const val BACK = "back"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    // KEYCODE_CHANNEL_UP / KEYCODE_CHANNEL_DOWN, same snake_case rule as volume_up.
    const val CHANNEL_UP = "channel_up"
    const val CHANNEL_DOWN = "channel_down"
    // Menu key on the Fire TV remote. The Amazon app labels this button "menu".
    const val OPTIONS = "menu"
    // Power toggle. The Amazon app sends this as a normal key press.
    const val POWER = "power"
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val FAST_FORWARD = "fast_forward"
    const val REWIND = "rewind"

    val repeating = setOf(
        UP,
        DOWN,
        LEFT,
        RIGHT,
        VOLUME_UP,
        VOLUME_DOWN,
        CHANNEL_UP,
        CHANNEL_DOWN,
        FAST_FORWARD,
        REWIND,
    )

    val media = setOf(PLAY, PAUSE, FAST_FORWARD, REWIND)

    fun isKnown(action: String): Boolean {
        return action.matches(Regex("[a-z0-9_]+")) && action.length in 1..32
    }
}
