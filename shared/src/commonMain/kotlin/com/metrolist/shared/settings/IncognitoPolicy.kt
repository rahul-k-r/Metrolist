/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.settings

import com.russhwolf.settings.Settings as KmpSettings

/**
 * Full-app incognito: while active, listening history, the YouTube playback report and search
 * history are suppressed and radio requests are sent without the account. Likes, library and
 * playlist edits are intentional actions and are not affected.
 *
 * [isIncognito] is the single predicate every sink reads, so the toggle and the behaviour cannot
 * disagree. The master switch gates everything: turning it off makes a stale active flag inert.
 */
object IncognitoPolicy {
    const val ENABLED_KEY = "incognitoModeEnabled"
    const val SHOW_IN_MENU_KEY = "incognitoShowInProfileMenu"
    const val RESET_ON_RESTART_KEY = "incognitoResetOnRestart"
    const val ACTIVE_KEY = "incognitoActive"

    fun isIncognito(
        modeEnabled: Boolean,
        active: Boolean,
    ): Boolean = modeEnabled && active

    /** The quick toggle is offered only once the master switch is on and the menu entry is allowed. */
    fun showQuickToggle(
        modeEnabled: Boolean,
        showInProfileMenu: Boolean,
    ): Boolean = modeEnabled && showInProfileMenu

    /** Clears the active flag at process start unless the user chose to keep it across restarts. */
    internal fun resetOnStart(settings: KmpSettings) {
        if (settings.getBoolean(RESET_ON_RESTART_KEY, true) && settings.getBoolean(ACTIVE_KEY, false)) {
            settings.putBoolean(ACTIVE_KEY, false)
        }
    }
}
