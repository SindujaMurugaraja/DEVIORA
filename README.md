# DEVIORA
## INTELLIGENT FAILURE PREDICTION AND RISK ANALYSIS

DEVIORA is a machine learning-based application designed to predict potential engine failures, analyze risk levels, identify failure patterns, and provide recommendations using engine performance parameters.

### 🚀 Key Features
- Engine Failure Prediction (PASS / FAIL)
- Machine Learning-based classification using Random Forest
- Risk Level Assessment
- Failure Pattern Identification
- Feature Importance Analysis
- Recommended Corrective Actions
- CSV Dataset Upload and Analysis
- Interactive Web Interface

### 🛠️ Technologies Used
- **Programming Language:** Java
- **Machine Learning:** Weka – Random Forest
- **Frontend:** HTML, CSS, JavaScript
- **Dataset:** CSV

### 📊 Project Workflow
1. Upload engine performance dataset.
2. Process and validate input parameters.
3. Train and evaluate the Random Forest model.
4. Predict engine quality status.
5. Analyze failure patterns and risk levels.
6. Display recommendations and results.

### 📁 Project Structure
```text
DEVIORA/
├── dataset/
├── lib/
├── src/
│   └── DEVIORA.java
└── web/
    ├── index.html
    ├── style.css
    └── script.js
```

### ▶️ How to Run
Compile the Java application:

```bash
javac --add-modules jdk.httpserver -cp "lib/weka.jar" -d src src/DEVIORA.java
```

Run the application:

```bash
java --add-modules jdk.httpserver --add-opens java.base/java.lang=ALL-UNNAMED -cp "lib/weka.jar;src" DEVIORA
```

Open in your browser:

```text
http://localhost:8080
```

### 🎯 Objective
To support early identification of potential engine failures through machine learning-based prediction, risk analysis, and actionable recommendations.

### ⚠️ Note
This project is developed for academic and demonstration purposes. Prediction performance depends on the quality and representativeness of the dataset.

---
**Project Name:** DEVIORA  
**Title:** Intelligent Failure Prediction and Risk Analysis
