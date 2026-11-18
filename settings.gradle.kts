pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "ai-game"

// הספרייה המשותפת, לפי נתיב: כל בנייה של המשחק מקמפלת אותה מהקוד שלה,
// אז שינוי שם נכנס אוטומטית — בלי להעתיק jar ובלי לפרסם.
includeBuild("../aiagent-lib")
