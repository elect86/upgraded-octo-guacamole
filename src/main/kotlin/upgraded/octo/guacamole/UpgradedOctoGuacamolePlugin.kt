/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package upgraded.octo.guacamole

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * A simple 'hello world' plugin.
 */
class UpgradedOctoGuacamolePlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.dependencyResolutionManagement {
            it.versionCatalogs {
                it.create("libs") {
                    it.alias("groovy-core").to("org.codehaus.groovy:groovy:3.0.5")
                }
            }
        }
    }
}
