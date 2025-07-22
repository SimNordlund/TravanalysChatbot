Travolta – Din AI-baserade travassistent
Travolta är en smart och personlig travbot som hjälper dig analysera och förstå travlopp. Projektet använder Spring AI och integrerar OpenAI:s senaste språkmodeller för att ge relevanta, raka och datadrivna svar kring svenska travlopp.

Botens svar bygger på hästdata från svenska travbanor, och presenterar alltid analys, prestation, motstånd, tid och prispengar per häst. Analysen (procentanalys) är alltid det viktigaste värdet, vilket också styr ordningen i svaren. Fokus ligger på att förklara trav-data så enkelt och tydligt som möjligt, precis som om du snackar med en smart polare.



Funktioner
Naturlig konversation på svenska kring hästar, lopp och analyser.

Stöd för alla vanliga travtermer: analys, prestation, motstånd, tid och prispengar.

Skräll-detektor via Travanalys.se:s unika analysmodell.

Alltid datadrivna svar, sorterade efter högst analys.

Ger kontext kring bana (Solvalla, Dannero osv) för varje lopp.

Hämtar och sparar embeddings från PDF-dokument och vektordatabaser för effektiv sökning.

Modern, strömlinjeformad backend byggd med Spring Boot, OpenAI, och RAG (Retrieval Augmented Generation).



Teknisk översikt
Spring Boot backend (Java 21+)

OpenAI GPT-4 Turbo för chatt och AI-resonemang

SimpleVectorStore för lokal hantering av embeddings (PDF-data)

CORS-stöd för lokala och publika frontend-miljöer (travanalys.se)

Prompt engineering för att alltid leverera relevanta svar på svenska

PDF-ingest och tokenisering för egen travdokumentation och kunskap

Konfigurationsstyrd deploy för både lokal utveckling och moln (Render, Docker)




1. Komma igång
Klona repot:
git clone https://github.com/dittnamn/travolta.git
cd travolta

2. Konfigurera miljövariabler:
Skapa en .env-fil eller exportera SPRING_AI_OPENAI_API_KEY med din OpenAI-nyckel.

3. Bygg och starta:
./mvnw spring-boot:run
Backend körs då på port 8081 som standard.

4. API-endpoint för chatt:
Skicka meddelanden till /chat-stream?message=Ditt+meddelande

Miljövariabler & Konfiguration
Se application.properties för alla inställningar kring modell, retry-logik, port mm. Vektordata lagras i /tmp/vectorstore.json i produktionsläge.




Bidra / Förslag
Vill du bidra eller har förslag på förbättringar? Skapa gärna en issue eller PR direkt i repot.

Travolta är skapad för att göra travvärlden mer öppen, begriplig och transparent – oavsett om du är nybörjare eller rutinerad travnörd.

Frågor? Hör av dig!
