import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "com.aigame"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
    // sherpa-onnx (זיהוי דובר) מתפרסמת רק ב-JitPack — ורק אותה לוקחים משם.
    maven("https://jitpack.io") {
        content { includeGroup("com.github.k2-fsa.sherpa-onnx") }
    }
}

dependencies {
    // החלון, הפרצוף, והקלט מהמקלדת/קליקר.
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")

    // ViewModel ו-viewModelScope — אותו API כמו באנדרואיד.
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Dispatchers.Main על תהליכון ה-UI של Swing.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // המצב נשמר לדיסק כ-JSON.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("ch.qos.logback:logback-classic:1.6.3")

    // טביעת קול: מי מדבר, מקומית, בלי ענן (k2-fsa/sherpa-onnx).
    // הספרייה המקומית שלה — ל-Linux x64, המחשב שעליו המשחק רץ.
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-jvm:v1.13.8")
    runtimeOnly("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-linux-x64:v1.13.8")

    // הסוכן ולקוחות ה-LLM — מגיע מ-../aiagent-lib (ראו includeBuild ב-settings.gradle.kts).
    implementation("com.aiagent:llm-agent:0.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// ה-JDK המקומי הוא 26, אבל מקמפלים ל-21 — כמו בשרת של הדייט.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    // runCurrent / advanceUntilIdle / advanceTimeBy בבדיקות עדיין מסומנים כניסיוניים.
    sourceSets.named("test") {
        languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

compose.desktop {
    application {
        mainClass = "com.aigame.MainKt"
        // Skiko ניגש לספריות מקומיות. בלי זה JDK 26 מדפיס אזהרה בכל הרצה.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
    }
}

tasks.test {
    useJUnitPlatform()
}
