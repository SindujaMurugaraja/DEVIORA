let selectedFile = null;
let analysisData = null;
let selectedEngine = null;


/* =========================================================
   ELEMENTS
========================================================= */

const fileInput =
    document.getElementById("fileInput");

const chooseFile =
    document.getElementById("chooseFile");

const dropZone =
    document.getElementById("dropZone");

const fileName =
    document.getElementById("fileName");

const analyzeButton =
    document.getElementById("analyzeButton");

const loadingCard =
    document.getElementById("loadingCard");

const resultsSection =
    document.getElementById("resultsSection");

const engineList =
    document.getElementById("engineList");

const detailPanel =
    document.getElementById("detailPanel");


/* =========================================================
   FILE SELECTION
========================================================= */

chooseFile.addEventListener(
    "click",
    () => fileInput.click()
);


fileInput.addEventListener(
    "change",
    event => {

        const file =
            event.target.files[0];

        if (file) {
            selectFile(file);
        }
    }
);


dropZone.addEventListener(
    "dragover",
    event => {

        event.preventDefault();

        dropZone.classList.add(
            "dragover"
        );
    }
);


dropZone.addEventListener(
    "dragleave",
    () => {

        dropZone.classList.remove(
            "dragover"
        );
    }
);


dropZone.addEventListener(
    "drop",
    event => {

        event.preventDefault();

        dropZone.classList.remove(
            "dragover"
        );

        const file =
            event.dataTransfer.files[0];

        if (file) {
            selectFile(file);
        }
    }
);


function selectFile(file) {

    if (!file.name.toLowerCase().endsWith(".csv")) {

        alert(
            "Please select a CSV file."
        );

        return;
    }

    selectedFile = file;

    fileName.textContent =
        `${file.name} · ${(file.size / 1024).toFixed(1)} KB`;
}


/* =========================================================
   ANALYZE
========================================================= */

analyzeButton.addEventListener(
    "click",
    analyzeFile
);


async function analyzeFile() {

    if (!selectedFile) {

        alert(
            "Please choose a CSV dataset first."
        );

        return;
    }

    loadingCard.classList.remove(
        "hidden"
    );

    resultsSection.classList.add(
        "hidden"
    );

    analyzeButton.disabled = true;

    try {

        const csvText =
            await selectedFile.text();

        const response =
            await fetch(
                "/analyze",
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "text/plain"
                    },

                    body: csvText
                }
            );

        const data =
            await response.json();

        if (!response.ok) {

            throw new Error(
                data.error ||
                "Analysis failed."
            );
        }

        analysisData = data;

        renderDashboard(data);

        resultsSection.classList.remove(
            "hidden"
        );

        resultsSection.scrollIntoView({
            behavior: "smooth"
        });

    } catch (error) {

        console.error(error);

        alert(
            "DEVIORA could not analyze the file.\n\n" +
            error.message
        );

    } finally {

        loadingCard.classList.add(
            "hidden"
        );

        analyzeButton.disabled = false;
    }
}


/* =========================================================
   DASHBOARD
========================================================= */

function renderDashboard(data) {

    document.getElementById(
        "totalEngines"
    ).textContent = data.total;

    document.getElementById(
        "passCount"
    ).textContent = data.pass;

    document.getElementById(
        "failCount"
    ).textContent = data.fail;

    document.getElementById(
        "criticalCount"
    ).textContent = data.critical;

    document.getElementById(
        "donutTotal"
    ).textContent = data.total;

    document.getElementById(
        "passLegend"
    ).textContent = data.pass;

    document.getElementById(
        "failLegend"
    ).textContent = data.fail;


    renderDonut(data);

    renderSignatureChart(
        data.signatures
    );

    renderRiskBars(data);

    renderInvestigationQueue(
        data.engines
    );
}


/* =========================================================
   DONUT
========================================================= */

function renderDonut(data) {

    const total =
        data.pass + data.fail;

    if (total === 0) {
        return;
    }

    const passPercent =
        (data.pass / total) * 100;

    const failPercent =
        (data.fail / total) * 100;

    const donut =
        document.getElementById(
            "statusDonut"
        );

    donut.style.background =
        `conic-gradient(
            #159a68 0% ${passPercent}%,
            #e14b5f ${passPercent}% 100%
        )`;
}


/* =========================================================
   SIGNATURE CHART
========================================================= */

function renderSignatureChart(
    signatures
) {

    const chart =
        document.getElementById(
            "signatureChart"
        );

    chart.innerHTML = "";

    const entries =
        Object.entries(signatures);

    if (entries.length === 0) {

        chart.innerHTML =
            `<div style="
                color:#718096;
                font-size:12px;
            ">
                No failure signatures detected.
            </div>`;

        return;
    }

    entries.sort(
        (a, b) => b[1] - a[1]
    );

    const max =
        Math.max(
            ...entries.map(
                item => item[1]
            )
        );

    entries.forEach(
        ([name, count]) => {

            const row =
                document.createElement(
                    "div"
                );

            row.className =
                "signature-row";

            const percentage =
                (count / max) * 100;

            row.innerHTML = `
                <div class="signature-name">
                    ${escapeHTML(name)}
                </div>

                <div class="signature-track">
                    <div
                        class="signature-fill"
                        style="width:${percentage}%"
                    ></div>
                </div>

                <div class="signature-count">
                    ${count}
                </div>
            `;

            chart.appendChild(row);
        }
    );
}


/* =========================================================
   RISK BARS
========================================================= */

function renderRiskBars(data) {

    const values = [
        {
            id: "critical",
            value: data.critical
        },
        {
            id: "high",
            value: data.high
        },
        {
            id: "medium",
            value: data.medium
        },
        {
            id: "low",
            value: data.low
        }
    ];

    const max =
        Math.max(
            1,
            ...values.map(
                item => item.value
            )
        );

    values.forEach(item => {

        const bar =
            document.getElementById(
                item.id + "Bar"
            );

        const count =
            document.getElementById(
                item.id + "Risk"
            );

        bar.style.width =
            `${(item.value / max) * 100}%`;

        count.textContent =
            item.value;
    });
}


/* =========================================================
   INVESTIGATION QUEUE
========================================================= */

function renderInvestigationQueue(
    engines
) {

    engineList.innerHTML = "";

    const failures =
        engines
            .filter(
                engine =>
                    engine.prediction === "FAIL"
            )
            .sort(
                (a, b) =>
                    b.riskScore -
                    a.riskScore
            );

    document.getElementById(
        "queueCount"
    ).textContent =
        `${failures.length} failures`;

    if (failures.length === 0) {

        engineList.innerHTML =
            `
            <div style="
                color:#718096;
                font-size:12px;
                padding:15px;
            ">
                No failed engines detected.
            </div>
            `;

        return;
    }

    failures.forEach(
        (engine, index) => {

            const item =
                document.createElement(
                    "div"
                );

            item.className =
                "engine-item";

            item.dataset.id =
                engine.id;

            item.innerHTML = `
                <div class="engine-top">

                    <span class="engine-id">
                        ${escapeHTML(engine.id)}
                    </span>

                    <span class="priority ${engine.priority}">
                        ${engine.priority}
                    </span>

                </div>

                <div class="engine-bottom">

                    <span class="signature-mini">
                        ${escapeHTML(engine.signature)}
                    </span>

                    <span>
                        Risk ${engine.riskScore}/100
                    </span>

                </div>
            `;

            item.addEventListener(
                "click",
                () => {

                    document
                        .querySelectorAll(
                            ".engine-item"
                        )
                        .forEach(
                            element =>
                                element.classList.remove(
                                    "selected"
                                )
                        );

                    item.classList.add(
                        "selected"
                    );

                    showEngineDetails(
                        engine
                    );
                }
            );

            engineList.appendChild(item);

            /*
             * Automatically open the first
             * critical/high engine.
             */
            if (
                index === 0 &&
                !selectedEngine
            ) {

                item.classList.add(
                    "selected"
                );

                showEngineDetails(
                    engine
                );
            }
        }
    );
}


/* =========================================================
   ENGINE DETAIL
========================================================= */

function showEngineDetails(
    engine
) {

    selectedEngine = engine;

    const factorRows =
        engine.factors
            .map(
                factor => {

                    const score =
                        Math.round(
                            factor.score
                        );

                    return `
                        <div class="factor-row">

                            <div class="factor-name">
                                ${escapeHTML(
                                    factor.name
                                )}
                            </div>

                            <div class="factor-track">
                                <div
                                    class="factor-fill"
                                    style="width:${score}%"
                                ></div>
                            </div>

                            <div class="factor-score">
                                ${score}%
                            </div>

                        </div>
                    `;
                }
            )
            .join("");


    const recommendations =
        engine.recommendations
            .map(
                item =>
                    `<li>${escapeHTML(item)}</li>`
            )
            .join("");


    const values =
        engine.values;


    detailPanel.innerHTML = `

        <div class="detail-top">

            <div>

                <div class="detail-id">
                    ${escapeHTML(engine.id)}
                </div>

                <div class="detail-signature">
                    ${escapeHTML(
                        engine.signature
                    )}
                </div>

            </div>

            <div class="risk-score">

                <strong>
                    ${engine.riskScore}
                </strong>

                <span>
                    RISK SCORE / 100
                </span>

            </div>

        </div>


        <div class="confidence-box">

            <div class="confidence-head">

                <span>
                    ML PREDICTION CONFIDENCE
                </span>

                <span>
                    ${engine.confidence.toFixed(1)}%
                </span>

            </div>

            <div class="confidence-track">

                <div
                    class="confidence-fill"
                    style="
                        width:${engine.confidence}%
                    "
                ></div>

            </div>

        </div>


        <div class="detail-section-title">
            TOP CONTRIBUTING ANOMALIES
        </div>

        <div class="factor-chart">
            ${factorRows}
        </div>


        <div class="detail-section-title">
            OPERATING SNAPSHOT
        </div>

        <div class="sensor-grid">

            <div class="sensor-card">
                <span>Temperature</span>
                <strong>
                    ${values.temperature} °C
                </strong>
            </div>

            <div class="sensor-card">
                <span>Oil Pressure</span>
                <strong>
                    ${values.oilPressure} bar
                </strong>
            </div>

            <div class="sensor-card">
                <span>RPM</span>
                <strong>
                    ${values.rpm}
                </strong>
            </div>

            <div class="sensor-card">
                <span>Vibration</span>
                <strong>
                    ${values.vibration} mm/s
                </strong>
            </div>

            <div class="sensor-card">
                <span>Torque</span>
                <strong>
                    ${values.torque} Nm
                </strong>
            </div>

            <div class="sensor-card">
                <span>Load</span>
                <strong>
                    ${values.load}%
                </strong>
            </div>

            <div class="sensor-card">
                <span>Runtime</span>
                <strong>
                    ${values.runtime} h
                </strong>
            </div>

            <div class="sensor-card">
                <span>Coolant</span>
                <strong>
                    ${values.coolant} °C
                </strong>
            </div>

            <div class="sensor-card">
                <span>Fuel Pressure</span>
                <strong>
                    ${values.fuelPressure} bar
                </strong>
            </div>

        </div>


        <div class="recommendation-box">

            <h5>
                RECOMMENDED INSPECTION ACTIONS
            </h5>

            <ul>
                ${recommendations}
            </ul>

        </div>


        <p class="interpretation">
            DEVIORA identified this failure profile
            from the ML prediction and abnormal operating
            conditions. The factor scores represent
            normalized anomaly strength, not proven causal
            effects.
        </p>
    `;
}


/* =========================================================
   HTML ESCAPE
========================================================= */

function escapeHTML(value) {

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll(
            "'",
            "&#039;"
        );
}