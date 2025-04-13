const dropArea = document.getElementById('drop-area');
const fileInput = document.getElementById('file-input');
const fileButton = document.getElementById('file-button');
const fileName = document.getElementById('file-name');
const form = document.getElementById('upload-form');
const result = document.getElementById('result');

['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
  dropArea.addEventListener(eventName, preventDefaults, false);
  document.body.addEventListener(eventName, preventDefaults, false);
});

['dragenter', 'dragover'].forEach(eventName => {
  dropArea.addEventListener(eventName, highlight, false);
});

['dragleave', 'drop'].forEach(eventName => {
  dropArea.addEventListener(eventName, unhighlight, false);
});

dropArea.addEventListener('drop', handleDrop, false);

fileButton.addEventListener('click', () => {
  fileInput.click();
});

fileInput.addEventListener('change', handleFiles);

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  result.innerHTML = '<div class="loading-text">Analyserar...</div>';

  const formData = new FormData(form);

  try {
    const response = await fetch('/upload', {
      method: 'POST',
      body: formData
    });

    const text = await response.text();
    result.innerHTML = '<h2>Resultat på analys</h2><div id="result-content">'
        + text.replace(
            /\n/g, '<br>') + '</div>';
  } catch (error) {
    result.innerHTML = '<div class="loading-text">Error: ' + error.message
        + '</div>';
  }
});

function preventDefaults(e) {
  e.preventDefault();
  e.stopPropagation();
}

function highlight() {
  dropArea.classList.add('highlight');
}

function unhighlight() {
  dropArea.classList.remove('highlight');
}

function handleDrop(e) {
  const dt = e.dataTransfer;
  const files = dt.files;
  handleFiles(files);
}

function handleFiles(e) {
  const files = e.target?.files || e;
  if (files.length) {
    fileInput.files = files;
    updateFileName(files[0].name);
  }
}

function updateFileName(name) {
  fileName.textContent = "Vald fil: " + name;
}

const chatForm = document.getElementById('chat-form');
const chatInput = document.getElementById('chat-input');
const chatMessages = document.getElementById('chat-messages');

appendMessage(
    "Halloj! Jag är ditt bollplank för amorteringsfrågor. Hur kan jag hjälpa dig idag?",
    "bot");

chatForm.addEventListener('submit', async function (e) {
  e.preventDefault();

  const message = chatInput.value.trim();
  if (!message) {
    return;
  }

  chatInput.value = '';

  appendMessage(message, "user");

  try {
    const loadingDiv = document.createElement('div');
    loadingDiv.className = 'message bot-message';
    loadingDiv.textContent = 'Grubblar...';
    chatMessages.appendChild(loadingDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;

    const response = await fetch(
        '/chat-stream?message=' + encodeURIComponent(message));
    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    loadingDiv.textContent = '';

    while (true) {
      const {value, done} = await reader.read();
      if (done) {
        break;
      }

      const text = decoder.decode(value, {stream: true});
      loadingDiv.textContent += text;
      chatMessages.scrollTop = chatMessages.scrollHeight;
    }
  } catch (error) {
    appendMessage("Det uppstod ett fel: " + error.message, "bot");
  }
});

function appendMessage(text, sender) {
  const messageDiv = document.createElement('div');
  messageDiv.className = `message ${sender}-message`;
  messageDiv.textContent = text;
  chatMessages.appendChild(messageDiv);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}