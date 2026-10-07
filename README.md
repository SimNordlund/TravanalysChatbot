# Travolta – AI-baserad travassistent

Travolta är en svensk travassistent som kombinerar loppdata, RAG, verktygsanrop och live-sökning för att ge tydliga och datadrivna analyser av svenska travlopp.

Assistenten kan bland annat analysera hästar och lopp, hämta startlistor och väder, söka på webben, hantera röstinmatning och skicka analyser via e-post. Svaren prioriterar procentanalys, prestation, motstånd, tider och prispengar.

## Funktioner

- Strömmande svensk chatt med konversationsminne.
- GPT-6 Luna för huvudchatten och OpenAI-baserad webbsökning.
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
| Chattmodell | `gpt-6-luna` |
| Embeddings | `text-embedding-3-large` |
| Transkribering | `gpt-4o-transcribe` |
| Databas | PostgreSQL |
| Talsyntes | Azure Speech |

Huvudchatten använder Spring AI:s Chat Completions-integration. Eftersom Travolta använder function calling körs GPT-6 Luna med `reasoning-effort=none`. Den separata webbsökningsfunktionen använder OpenAI Responses API.

## Förutsättningar

- Java 21.
- En PostgreSQL-databas med projektets förväntade tabeller. Hibernate kör med `ddl-auto=validate` och skapar därför inte schemat.
- En OpenAI API-nyckel med åtkomst till `gpt-6-luna`.
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

Vid första starten skapas vektorlagret från den inkluderade PDF-filen om `VECTORSTORE_FILEPATH` inte redan finns. Detta använder OpenAI-embeddings och kräver därför en fungerande API-nyckel.

## API

### Strömmande chatt

```http
GET /chat-stream?message=Vilka%20hastar%20ar%20mest%20intressanta&conversationId=min-session
```

- `message` är obligatorisk.
- `conversationId` är valfri men bör återanvändas för sammanhängande konversationer.
- Svaret strömmas som UTF-8 `text/plain`.

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

Skicka ljudfilen i fältet `file`. De valfria parametrarna `voice`, `speed` och `conversationId` kan anges som query-parametrar. Svaret innehåller text samt MP3-ljud som Base64.

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

PDF-underlaget finns i `src/main/resources/docs`. Vid första uppstarten delas dokumentet upp i mindre textstycken, bäddas in med `text-embedding-3-large` och sparas i `temp/vectorstore.json` eller den sökväg som anges med `VECTORSTORE_FILEPATH`.

`SimpleVectorStore` är avsett för den nuvarande mindre dokumentmängden. Vid betydligt större datamängder bör lagringen flyttas till exempelvis pgvector.

## CORS

Backend tillåter anrop från:

- `http://localhost:5173`
- `https://travanalys.onrender.com`
- `https://travanalys.se`
- `https://www.travanalys.se`

## Viktiga konfigurationsfiler

- `src/main/resources/application.properties` – modeller, databas, e-post, röst, retries och MCP.
- `src/main/resources/prompts/travPrompt.st` – systemprompt och svarsinstruktioner.
- `src/main/resources/docs` – PDF-underlag för RAG.
- `temp/vectorstore.json` – lokalt genererat vektorlager.

Se den [officiella dokumentationen för GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna) och [Spring AI 1.0-dokumentationen](https://docs.spring.io/spring-ai/reference/1.0/) för mer information om integrationerna.
