package com.gamboo.minbody

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.awt.Desktop
import java.net.URI

@SpringBootApplication
class MinbodyApplication

fun main(args: Array<String>) {
    runApplication<MinbodyApplication>(*args)
}

@Component
class BrowserLauncher {
    @EventListener(ApplicationReadyEvent::class)
    fun launchBrowser() {
        val url = "http://localhost:5000"
        val os = System.getProperty("os.name").lowercase()
        
        try {
            when {
                os.contains("win") -> {
                    // Windows
                    ProcessBuilder("cmd", "/c", "start", url).start()
                    println("Browser opened at $url")
                }
                os.contains("mac") -> {
                    // macOS
                    ProcessBuilder("open", url).start()
                    println("Browser opened at $url")
                }
                os.contains("nix") || os.contains("nux") -> {
                    // Linux
                    val browsers = listOf("xdg-open", "google-chrome", "firefox", "mozilla", "opera", "epiphany", "konqueror", "netscape")
                    var opened = false
                    for (browser in browsers) {
                        try {
                            ProcessBuilder(browser, url).start()
                            println("Browser opened at $url with $browser")
                            opened = true
                            break
                        } catch (e: Exception) {
                            // Try next browser
                        }
                    }
                    if (!opened) {
                        println("Could not open browser. Please navigate to $url manually.")
                    }
                }
                else -> {
                    // Fallback to Desktop API
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI(url))
                        println("Browser opened at $url")
                    } else {
                        println("Could not detect OS. Please navigate to $url manually.")
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to open browser: ${e.message}")
            println("Please navigate to $url manually.")
        }
    }
}