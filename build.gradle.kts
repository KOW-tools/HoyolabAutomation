// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

extra["appVersionCode"] = getVersionCode()
extra["appVersionName"] = getVersionName()

fun gitOutput(vararg args: String): String {
    return providers.exec {
        commandLine("git", *args)
        workingDir(rootDir)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}

fun getGitCommitCount(): Int {
    return gitOutput("rev-list", "--count", "HEAD").toInt()
}

fun getGitDescribe(): String {
    return gitOutput("describe", "--tags", "--abbrev=0")
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return commitCount
}

fun getVersionName(): String {
    return getGitDescribe()
}
