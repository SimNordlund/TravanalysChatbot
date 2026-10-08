# Travolta – AI-baserad travassistent

Travolta är en svensk travassistent som kombinerar loppdata, RAG, verktygsanrop och live-sökning för att ge tydliga och datadrivna analyser av svenska travlopp.

Assistenten kan analysera hästar och lopp, hämta registrerade resultat, startlistor och väder, hjälpa till med webbplatsens funktioner, söka på webben, hantera röstinmatning och skicka analyser via e-post på användarens begäran. Svaren skiljer mellan analysvärden, bedömningar och registrerade loppresultat.

## Funktioner

- Svensk chatt med konversationsminne. Textsvaret skickas när modellens verktygsrunda är klar.
- Gemensamma systeminstruktioner för text och tal, med aktuell tid i Europe/Stockholm.
- Webbplatsguide för Analys, Ranking, Spel & ROI och reducering, verifierad mot lokal webbplatskod.
- Registrerade placeringar från `roi.resultat`, med tydlig markering av saknade och motstridiga uppgifter.
- GPT-6.1 Sol med reasoning för huvudchatten, ämneskontrollen och webbsökningen.
- Verktygsanrop för travdata, startlistor, väder, andelsköp och e-post.
- RAG över inbäddade PDF-dokument med lokal `SimpleVectorStore`.
- REST-endpoint för topplistor per datum, bana och spelform.
- Tal-till-text med OpenAI och text-till-tal med Azure Speech.
- PostgreSQL för trav- och analysdata.
- Valfri MCP-klient för väderdata via Open Meteo.

## Teknik

| Komponent | Version eller tjänst |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring AI | 1.0.9 |
| Chattmodell | `gpt-6.1-sol` |
| Embeddings | `text-embedding-3-large` |
| Transkribering | `gpt-4o-transcribe` |
| Databas | PostgreSQL |
| Talsyntes | Azure Speech |

Huvudchatten använder Spring AI:s `ChatClient`, minne, RAG och verktygsdefinitioner. En lokal adapter skickar modell- och verktygsanrop via OpenAI Responses API, eftersom GPT-6.1 Sol kräver Responses för function calling med reasoning. `app.openai.chat.reasoning-effort=medium` är standard. Adaptern skickar tillbaka verktygsresultat och krypterade reasoning-objekt i samma frågerunda utan att lagra svar hos OpenAI (`store=false`). Chattens HTTP-endpoint behåller sitt gränssnitt men levererar texten efter att modellens verktygsrunda avslutats. Den separata webbsökningen använder också Responses API.

## Förutsättningar

- Java 21.
- En PostgreSQL-databas med projektets förväntade tabeller. Hibernate kör med `ddl-auto=validate` och skapar därför inte schemat.
- En OpenAI API-nyckel med åtkomst till `gpt-6.1-sol`.
- Azure Speech-uppgifter om röstfunktionerna ska användas.
- Node.js och `npx` om MCP-väderklienten ska vara aktiverad.

Maven Wrapper ingår i projektet, så en separat Maven-installation behövs inte.

## Konfiguration

Skapa filen `.env` i projektroten. Den laddas automatiskt av Spring Boot och ska inte versionshanteras.

```properties
SPRING_AI_OPENAI_API_KEY=din_openai_nyckel

DATABASE_URL=jdbc:postgresql://localhost:5432/travolta
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=ditt_losenord

AZURE_SPEECH_KEY=din_azure_speech_nyckel
AZURE_SPEECH_REGION=westeurope
AZURE_SPEECH_VOICE=sv-SE-MattiasNeural

MAIL_USERNAME=din_epostadress
MAIL_PASSWORD=ditt_app_losenord
MAIL_FROM=din_epostadress

MCP_CLIENT_ENABLED=false
```

Ytterligare valfria inställningar:

| Variabel | Standardvärde | Beskrivning |
|---|---|---|
| `PORT` | `8081` | Serverport. |
| `VECTORSTORE_FILEPATH` | `temp/vectorstore.json` | Sökväg till lokal vektordata. |
| `CHAT_MEMORY_SESSION_TTL` | `PT6H` | Inaktivitetstid innan en chattsession förfaller. |
| `CHAT_MAX_OUTPUT_TOKENS` | `8000` | Tak per Responses-anrop, inklusive reasoning och svarstext. Ersätter `CHAT_MAX_COMPLETION_TOKENS`. |
| `CHAT_STREAM_TIMEOUT` | `300s` | Timeout för chattsvar som kan behöva flera verktygsanrop. |
| `AI_LOG_LEVEL` | `INFO` | Loggnivå för Spring AI. Använd `DEBUG` endast vid felsökning. |
| `MCP_CLIENT_ENABLED` | `true` | Aktiverar MCP-klienten. Sätt till `false` för enklast lokal start. |
| `MCP_REQUEST_TIMEOUT` | `15s` | Timeout för MCP-anrop. |
| `MCP_WEATHER_COMMAND` | `npx` | Kommando som startar väderservern. |

## Starta lokalt

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS eller Linux:

```bash
./mvnw spring-boot:run
```

Backend startar normalt på `http://localhost:8081`.

Vid första starten skapas vektorlagret från den inkluderade PDF-filen om `VECTORSTORE_FILEPATH` inte redan finns. Detta använder OpenAI-embeddings. Om dokumentladdningen misslyckas kan tjänsten starta med webbplatsguiden och databasverktygen; felet loggas. En misslyckad dokumentsökning blockerar inte själva chattsvaret.

## API

### Strömmande chatt

```http
GET /chat-stream?message=Vilka%20hastar%20ar%20mest%20intressanta&conversationId=min-session
```

- `message` är obligatorisk.
- `conversationId` är valfri men bör återanvändas för sammanhängande konversationer.
- Svaret strömmas som UTF-8 `text/plain`.
- `X-Conversation-Id` innehåller det använda konversations-id:t. Återanvänd det även vid byte mellan text och tal.
- Tomma frågor avvisas; radbrytningar i frågor och systemval bevaras.

### Travanalys per dag

```http
GET /api/trav/analysis/top-by-day?date=2026-09-30&track=Solvalla&form=vinnare&topN=3
```

- `date` och `track` är obligatoriska.
- `form` har standardvärdet `vinnare`.
- `topN` har standardvärdet `3` och maximalt värde `20`.

### Röstchatt

```http
POST /voice/chat
Content-Type: multipart/form-data
```

Skicka ljudfilen i fältet `file`. De valfria parametrarna `voice`, `speed` och `conversationId` kan anges som query-parametrar. Svaret behåller fälten `text` och `audioBase64` och innehåller även `transcript`, `speechDetected`, `conversationId` och `audioAvailable`.

Kontrollera `audioAvailable` och att `audioBase64` inte är tomt innan ljud spelas. Om talsyntesen misslyckas behålls textsvaret och `audioError` förklarar problemet. Vid tom transkribering ges en kort uppmaning att spela in igen utan att en tom fråga skickas till chattmodellen. Standardrösten hämtas från `AZURE_SPEECH_VOICE`; talhastigheten begränsas till 0,5–2,0.

### Transkribering

```http
POST /voice/transcribe
Content-Type: multipart/form-data
```

Skicka ljudfilen i fältet `file`. Svaret innehåller den svenska transkriberingen i fältet `text`.

### Talsyntes

```http
POST /voice/tts
Content-Type: application/json

{
  "text": "Välkommen till Travolta",
  "voice": "sv-SE-MattiasNeural",
  "speed": 1.0
}
```

Svaret innehåller MP3-ljud som Base64 i fältet `audioBase64`.

## RAG och vektordata

`TravoltaPromptService` kombinerar huvudinstruktionerna och webbplatsguiden med datum/tid för varje fråga. Röstläget lägger till en egen kort talinstruktion utan att ersätta grundkunskapen. Före både text- och röstchatten bedömer `TravoltaScopeGuard` den aktuella frågan mot trav och Travanalys; tydliga följdfrågor får använda en kort del av samtalets historik. Frågor utanför ämnet får ett fast svar utan att chattens sökverktyg körs. Vanliga hälsningar och uppenbara travfrågor kan avgöras direkt. Övriga frågor ger ett separat modellanrop; om det anropet fallerar får tydliga travfrågor ändå gå vidare. Konversationsminnet sparar upp till 20 meddelanden och körs före dokumenthämtningen, så att ursprungliga användarfrågor sparas utan upprepade PDF-utdrag.

PDF-underlaget finns i `src/main/resources/docs`. Vid första uppstarten delas dokumentet upp i mindre textstycken, bäddas in med `text-embedding-3-large` och sparas i `temp/vectorstore.json` eller den sökväg som anges med `VECTORSTORE_FILEPATH`.

Dokumentsökningen hämtar högst fyra relevanta utdrag. PDF-materialet används som bakgrund och ska inte bekräfta aktuella lopp, väder eller webbplatsfunktioner. Webbplatsguiden laddas direkt från resurser och kräver ingen ny embedding när den uppdateras. Den behöver uppdateras när webbplatsens funktioner ändras.

`SimpleVectorStore` är avsett för den nuvarande mindre dokumentmängden. Vid betydligt större datamängder bör lagringen flyttas till exempelvis pgvector.

## Datatolkning och källor

Svenska kortdatum tolkas dag/månad och saknat år utifrån aktuell svensk tid. Ogiltiga datum eller oklara banor ger ingen gissad träff. Analys-, startliste- och resultatverktyg använder faktiska loppnummer: en spelavdelning som V85-1 måste först kopplas till rätt lopp. En uttryckligen vald spelform ersätts inte automatiskt med vinnare.

`race_results_by_date_track_lap` hämtar lagrade placeringar från `roi.resultat` för hästarna i valt lopp och vald spelform. Svaret anger status och kan vara ofullständigt. De äldre `results_by_*`-verktygen ger analysrader, inte officiella resultat. Analysprocent är inte automatiskt vinstsannolikhet eller spelprocent, och en saknad placering räknas inte som förlust.

Webbsökningen behåller URL-annoteringar och kontrolltid så att Travolta kan hänvisa till sidorna som stöder svaret. API-strukturen följer [OpenAI:s dokumentation om webbsökning och källor](https://developers.openai.com/api/docs/guides/tools-web-search). Nätverksfel och svar utan källor rapporteras som att uppgiften inte kunde verifieras.

## CORS

Backend tillåter anrop från:

- `http://localhost:5173`
- `https://travanalys.onrender.com`
- `https://travanalys.se`
- `https://www.travanalys.se`

## Viktiga konfigurationsfiler

- `src/main/resources/application.properties` – modeller, databas, e-post, röst, retries och MCP.
- `src/main/resources/prompts/travPrompt.st` – gemensam systemprompt för korrekthet, dataval och svarsstil.
- `src/main/resources/prompts/voicePrompt.txt` – tillägg för naturliga, korta talade svar.
- `src/main/resources/prompts/ragContext.st` – separat mall för dokumentutdrag.
- `src/main/resources/knowledge/travanalys-guide.txt` – webbplatsens funktioner, navigering och användningshjälp.
- `src/main/resources/docs` – PDF-underlag för RAG.
- `temp/vectorstore.json` – lokalt genererat vektorlager.

Se den [officiella dokumentationen för GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol) och [Spring AI 1.0-dokumentationen](https://docs.spring.io/spring-ai/reference/1.0/) för mer information om integrationerna.
