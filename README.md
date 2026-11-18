# ai-game — המארח

אפליקציית PC שמנהלת ערב משחקי שתייה. על המסך יש פרצוף שישן, מתעורר בזמנים
אקראיים כדי לתת משימות, ומקשיב כשלוחצים על הכפתור.

Kotlin + Compose Desktop. המוח הוא Claude. הסוכן ולקוחות ה-LLM מגיעים מהספרייה המשותפת
[llm-agent](../aiagent-lib).

## הרצה

```bash
cp .env.example .env      # ולמלא ANTHROPIC_API_KEY
JAVA_HOME=$HOME/.jdks/openjdk-26.0.1 ./gradlew run
```

הספרייה מחוברת לפי נתיב (`includeBuild("../aiagent-lib")` ב-`settings.gradle.kts`): כל בנייה
של המשחק מקמפלת אותה מהקוד שלה. שינוי ב-`aiagent-lib` נכנס בבנייה הבאה — בלי להעתיק jar
ובלי לפרסם. היא צריכה לשבת ליד `ai-game`, באותה תיקייה.

| מקש | מה הוא עושה |
|---|---|
| רווח / PageUp / PageDown | להחזיק = הוא מקשיב. PageUp/PageDown הם מה שקליקר של מצגות שולח |
| לחיצה ארוכה על הפרצוף | אותו דבר, עם העכבר |
| F1 | פאנל פיתוח: להקליד במקום לדבר, להעיר אותו, יומן |
| F11 | מסך מלא |

## הארכיטקטורה

```
 view/                      viewmodel/                 repository/                     orchestrator/
┌──────────────┐ UiEvent   ┌───────────────┐ פונקציות  ┌───────────────────┐ היסטוריה+קלט ┌──────────────────┐
│ PartyScreen  │─────────▶ │ PartyViewModel│─────────▶ │ MicrophoneRepo    │              │ HostOrchestrator │
│ FaceView     │           │               │           │ HostRepo ─────────┼─────────────▶│ (בלי זיכרון)     │
│ SpeechPlayer │ ◀──────── │   UiState     │           │ WakeUp · Voice    │◀─────────────┤                  │
└──────────────┘           └───────▲───────┘           └─────────┬─────────┘  HostOutput  └──────────────────┘
  תמונה · טקסט · קול               │ מאזין                       │ updateX / addHostEvent
                           ┌───────┴─────────────────────────────▼─────────────────────────┐
                           │ AppStateManager — מקור האמת היחיד                              │
                           │  partyState · hostState · microphoneState ·                   │
                           │  environmentState · hostEventFlow                             │
                           └───────────────────────────────────────────────────────────────┘
```

- **`state/`** — רק קבצים שנגמרים ב-`State`, ומעליהם `AppStateManager`. כל state הוא
  `StateFlow` לקריאה עם `updateX { it.copy(...) }` לשינוי. אירועים (מה שנשמע, התעוררות)
  עוברים ב-`hostEventFlow`.
- **`orchestrator/`** — `HostOrchestrator`, המנצח. בלי זיכרון: מקבל היסטוריה וקלט, מחזיר
  `HostOutput` (מה לומר, איזו הבעה, מי נרשם). לא נוגע בשום מצב.
- **`repository/`** — חסרי מצב, כל אחד עם תפקיד אחד. מפעילים את המכשירים ואת המנצח,
  ומיישמים את מה שחזר על ה-states.
- **`viewmodel/`** — מאזין ל-states ומתרגם ל-`UiState`. אירוע מהמסך הופך לפונקציה ברפוזיטורי.
- **`view/`** — מאזין ל-`UiState` ומציג: הפרצוף, הכתובית, והקול. כשסיים להקריא —
  `SpeechFinished`, והמארח עובר לאירוע הבא.

| Repository | התפקיד | מעדכן |
|---|---|---|
| `MicrophoneRepository` | הכפתור: מקליט, מזהה דיבור, שולח `Heard` / `Misheard` | `MicrophoneState` |
| `HostRepository` | אירוע → מגריל שחקנים → `HostOrchestrator` → מיישם את הפלט. ער / ישן | `HostState`, `PartyState` |
| `AgentRepository` | המוח: מפעיל את המנצח, ובכישלון מחזיר משפט קבוע | `EnvironmentState` (שגיאה) |
| `VoiceRepository` | ממיר משפט לאודיו, בקול ובטון שהמנצח בחר | — |
| `WakeUpRepository` | השעון המעורר: התעוררות בזמן אקראי | `PartyState` (המועד הבא) |

| State | מה הוא מחזיק | נשמר לדיסק |
|---|---|---|
| `PartyState` | שחקנים, יומן הערב, ההתעוררות הבאה | כן |
| `HostState` | ישן / ער / חושב / מדבר, הבעה, כתובית | לא |
| `MicrophoneState` | מקשיב עכשיו? עוצמה | לא |
| `EnvironmentState` | מה מחובר: מוח, שמיעה, מיקרופון. שגיאה אחרונה | לא |

## שמירה בין הפעלות

`PartyStorage` נטען בעלייה, ומאזין ל-`partyState`: כל שינוי נכתב ל-`data/party.json`.
הכתיבה אטומית: קובץ זמני, `force` לדיסק, ואז החלפה בשם. אחרי נפילת חשמל נשאר או המצב
הקודם או החדש, אף פעם לא חצי קובץ.

המנצח לא זוכר כלום בין תורות: בכל אירוע `HostRepository` מעביר לו את השחקנים ואת סוף
היומן מ-`PartyState`. לכן הפעלה מחדש שקופה לו לגמרי.

מה שלא נשמר, ובכוונה: הפרצוף, המיקרופון והכתובית — אין טעם להתעורר באמצע משפט שנקטע.

מסיבה חדשה = למחוק את `data/party.json`.

## קול ושמיעה — Gemini

עם `GOOGLE_API_KEY` ב-`.env` — מפתח מ-[Google AI Studio](https://aistudio.google.com):

- **קול:** מודלי ההקראה של Gemini. המנצח בוחר לכל משפט קול וטון (`set_voice`):

  | קול | מודל | מתי |
  |---|---|---|
  | `CHARON_PRO` | Pro — עמוק ועשיר, איטי בכשתי שניות | רגעים גדולים |
  | `PUCK` | Flash — קליל ושמח | ברירת המחדל |
  | `FENRIR` | Flash — נרגש ומלא אנרגיה | התלהבות |

  הטון הוא הוראת משחק חופשית ("whispering like a villain"), שנשלחת יחד עם הטקסט.
- **שמיעה:** ההקלטה נשלחת ל-Gemini עם הוראה לתמלל בעברית. שמות השחקנים נכנסים להוראה.

שניהם ב-`speech/` — שירות שממיר קול↔טקסט, לא סוכן. בלי המפתח: כתובית ופה שזז במקום
קול, והקלדה ב-F1 במקום דיבור. אם Gemini לא עונה באמצע הערב, המארח ממשיך בכתובית.

## מה עוד לא אמיתי

| | עכשיו | בהמשך |
|---|---|---|
| מצלמה | אין | Claude רואה את התמונה באותה קריאה. צ'ק-אין וזיהוי לפי בגדים |

המכשירים של המשתמש (מיקרופון, רמקול, ובהמשך מצלמה) הם interface אחד כל אחד ב-`view/devices/`,
ונבנים ב-`Main`. שירותי הדיבור הם interface ב-`speech/`, ונבנים ב-`AppRepository.create()`.

## בדיקות

```bash
JAVA_HOME=$HOME/.jdks/openjdk-26.0.1 ./gradlew test
```

מודל מזויף שעונה לפי תסריט. נבדק ש:

- שחקן נרשם ומקבל תשובה.
- המצב שורד "הפעלה מחדש".
- ההגרלה הוגנת.
- בלי מפתח המארח עדיין אומר משהו.
- קובץ פגום לא מפיל את האפליקציה.

שיחה אמיתית מול Claude, דרך כל השכבות חוץ מהמסך. היא עולה כמה סנטים, וכותבת לתיקייה
זמנית ולא ל-`data/`:

```bash
LIVE_HOST_CHECK=1 JAVA_HOME=$HOME/.jdks/openjdk-26.0.1 ./gradlew test --tests '*LiveHostCheck*'
```

לראות את הפרצוף בכל המצבים בלי לפתוח חלון:

```bash
SNAPSHOTS_DIR=/tmp/faces JAVA_HOME=$HOME/.jdks/openjdk-26.0.1 ./gradlew test --tests '*FaceSnapshots*'
```

## מבנה

```
src/main/kotlin/com/aigame/
  Main.kt                         החלון. מכיר רק את AppRepository ואת המסך
  view/                           PartyScreen · FaceView · SpeechPlayer · DevPanel
  viewmodel/                      PartyViewModel · UiState
  repository/                     AppRepository (שורש) · Host · Microphone · Agent · Voice · WakeUp
  orchestrator/                   HostOrchestrator (הפרומפט) · HostTurn (היסטוריה, קלט, פלט) · actions/
  state/                          AppStateManager · Party · Host · Microphone · Voice · Environment (State)
  storage/                        PartyStorage · FileStore
  objects/                        Player · LogEntry · HostEvent
  view/devices/                   Microphone · Speaker — מה שבצד של המשתמש
  speech/                         SpeechToText · TextToSpeech · Google — קול ↔ טקסט. שירות, לא סוכן
```

`AiAgent`, `LlmClient` ושאר מסגרת הסוכן — ב-[aiagent-lib](../aiagent-lib).

כדי לראות כל קלט ופלט של הסוכן: ב-`src/main/resources/logback.xml` לשנות את
`com.aiagent.lib` ל-`DEBUG`.
