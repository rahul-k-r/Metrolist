package com.metrolist.desktop.utils

import java.awt.Color
import java.awt.EventQueue
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Toolkit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JWindow
import javax.swing.border.EmptyBorder

/**
 * A transient message near the taskbar. The tray library has no notification API and the main window may be
 * hidden, so this is a small non-focusable window that never takes input focus from other apps.
 */
internal object TrayToast {
    private const val VISIBLE_MS = 1800L
    private val scheduler =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tray-toast").apply { isDaemon = true } }
    private var window: JWindow? = null
    private var hideTask: ScheduledFuture<*>? = null

    fun show(message: String) {
        if (message.isBlank() || GraphicsEnvironment.isHeadless()) return
        EventQueue.invokeLater {
            val toast = window ?: createWindow().also { window = it }
            val label = toast.contentPane.getComponent(0) as JLabel
            label.text = message
            toast.pack()
            val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
            val bounds = config.bounds
            val margin = 16
            toast.setLocation(
                bounds.x + bounds.width - insets.right - toast.width - margin,
                bounds.y + bounds.height - insets.bottom - toast.height - margin,
            )
            toast.isVisible = true
            synchronized(this) {
                hideTask?.cancel(false)
                hideTask = scheduler.schedule({ EventQueue.invokeLater { toast.isVisible = false } }, VISIBLE_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun createWindow(): JWindow =
        JWindow().apply {
            focusableWindowState = false
            isAlwaysOnTop = true
            background = Color(0, 0, 0, 0)
            val label =
                object : JLabel() {
                    override fun paintComponent(g: Graphics) {
                        val g2 = g.create() as Graphics2D
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.color = Color(32, 32, 36, 235)
                        g2.fillRoundRect(0, 0, width, height, 20, 20)
                        g2.dispose()
                        super.paintComponent(g)
                    }
                }.apply {
                    foreground = Color.WHITE
                    font = font.deriveFont(Font.PLAIN, 14f)
                    border = EmptyBorder(10, 16, 10, 16)
                    isOpaque = false
                }
            contentPane.add(label)
        }
}
