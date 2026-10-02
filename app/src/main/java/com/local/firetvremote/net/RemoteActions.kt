package com.local.firetvremote.net

object RemoteActions {
    const val UP = "dpad_up"
    const val DOWN = "dpad_down"
    const val LEFT = "dpad_left"
    const val RIGHT = "dpad_right"
    const val SELECT = "select"
    const val BACK = "back"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    const val CHANNEL_UP = "channel_up"
    const val CHANNEL_DOWN = "channel_down"
    const val INPUT = "input"

    val repeating = setOf(UP, DOWN, LEFT, RIGHT, VOLUME_UP, VOLUME_DOWN, CHANNEL_UP, CHANNEL_DOWN)

    fun isKnown(action: String): Boolean {
        return action.matches(Regex("[a-z0-9_]+")) && action.length in 1..32
    }
}
