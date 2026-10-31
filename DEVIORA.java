import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
public class DEVIORA
{
    static final String WEB_PATH = "web";
    static final String DATASET_PATH = "dataset";
    static final String DEFAULT_DATASET = "DEVIORA_engine_dataset.csv";
    static final int PORT = 8080;
    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("======================================================");
        System.out.println("       DEVIORA - FAILURE INTELLIGENCE ENGINE");
        System.out.println("       Intelligent Failure Prediction & Risk Analysis");
        System.out.println("======================================================");
        Files.createDirectories(Paths.get(WEB_PATH));
        Files.createDirectories(Paths.get(DATASET_PATH));
        Path dataset = Paths.get(DATASET_PATH, DEFAULT_DATASET);
        if (Files.exists(dataset)) {
            System.out.println();
            System.out.println("[OK] Dataset found:");
            System.out.println("    " + dataset.toAbsolutePath());
        } else {
            System.out.println();
            System.out.println("[WARNING] Dataset not found:");
            System.out.println("    " + dataset.toAbsolutePath());
            System.out.println();
            System.out.println("Place your CSV inside the dataset folder.");
        }
        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT),
                0  
        );
        server.createContext("/", DEVIORA::handleRoot);
        server.createContext("/analyze", DEVIORA::handleAnalyze);
        server.createContext("/health", DEVIORA::handleHealth);
        server.setExecutor(
                Executors.newFixedThreadPool(10)
        );
        server.start();
        System.out.println();
        System.out.println("======================================================");
        System.out.println(" SERVER STARTED");
        System.out.println(" URL : http://localhost:" + PORT);
        System.out.println("======================================================");
        System.out.println();
    }
    static void handleRoot(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested == null ||
                requested.equals("/") ||
                requested.isEmpty()) {
            requested = "/index.html";
        }
        Path root = Paths.get(WEB_PATH)
                .toAbsolutePath()
                .normalize();
        Path path = Paths.get(
                WEB_PATH,
                requested.substring(1)
        ).normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(root) ||
                !Files.exists(absolute) ||
                Files.isDirectory(absolute)) {
            send(
                    exchange,
                    404,
                    "text/plain",
                    "File not found."
            );
            return;
        }
        String contentType = getContentType(absolute);
        byte[] data = Files.readAllBytes(absolute);
        Headers headers = exchange.getResponseHeaders();
        headers.set(
                "Content-Type",
                contentType + "; charset=UTF-8"
        );
        headers.set(
                "Cache-Control",
                "no-store, no-cache, must-revalidate"
        );
        exchange.sendResponseHeaders(
                200,
                data.length
        );
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }
    static String getContentType(Path path) {
        String name = path.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return "text/html";
        }
        if (name.endsWith(".css")) {
            return "text/css";
        }
        if (name.endsWith(".js")) {
            return "application/javascript";
        }
        if (name.endsWith(".json")) {
            return "application/json";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") ||
                name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/plain";
    }
    static void handleHealth(HttpExchange exchange)
            throws IOException {
        String json =
                "{"
                        + "\"status\":\"online\","
                        + "\"project\":\"DEVIORA\","
                        + "\"engine\":\"RandomForest\","
                        + "\"port\":" + PORT
                        + "}";
        send(
                exchange,
                200,
                "application/json",
                json
        );
}
    static void handleAnalyze(HttpExchange exchange)
            throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (method.equals("OPTIONS")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        try {
            Instances data;
            if (method.equals("GET")) {
                String requestedFile =
                        getQuery(
                                exchange.getRequestURI().getRawQuery(),
                                "file"
                        );
                Path datasetPath;
                if (requestedFile == null ||
                        requestedFile.isBlank()) {
                    datasetPath =
                            Paths.get(
                                    DATASET_PATH,
                                    DEFAULT_DATASET
                            );
                } else {
                    Path datasetRoot =
                            Paths.get(DATASET_PATH)
                                    .toAbsolutePath()
                                    .normalize();
                    datasetPath =
                            Paths.get(
                                    DATASET_PATH,
                                    requestedFile
                            ).normalize()
                            .toAbsolutePath();
                    if (!datasetPath.startsWith(datasetRoot)) {
                        throw new IllegalArgumentException(
                                "Invalid dataset file path."
                        );
                    }
                }
                if (!Files.exists(datasetPath)) {
                    throw new IllegalArgumentException(
                            "Dataset not found. Put "
                                    + DEFAULT_DATASET
                                    + " inside the dataset folder."
                    );
                }
                System.out.println(
                        "[ANALYZE] Loading dataset: "
                                + datasetPath
                );
                data = loadCSV(datasetPath);
            } 
            else if (method.equals("POST")) {
                byte[] body =
                        exchange.getRequestBody()
                                .readAllBytes();
                if (body.length == 0) {

                    throw new IllegalArgumentException(
                            "Uploaded CSV is empty."
                    );
                }
                String bodyText =
                        new String(
                                body,
                                StandardCharsets.UTF_8
                        );
                if (looksLikeCSV(bodyText)) {
                    data = loadCSVFromText(bodyText);
                } else {
                    String contentType =
                            exchange.getRequestHeaders()
                                    .getFirst("Content-Type");
                    if (contentType != null &&
                            contentType
                                    .toLowerCase(Locale.ROOT)
                                    .contains("multipart/form-data")) {
                        String csv =
                                extractMultipartCSV(
                                        body,
                                        contentType
                                );
                        if (csv == null ||
                                csv.isBlank()) {
                            throw new IllegalArgumentException(
                                    "Could not read CSV from uploaded file."
                            );
                        }
                        data = loadCSVFromText(csv);
                    } else {
                        throw new IllegalArgumentException(
                                "The uploaded content is not a valid CSV file."
                        );
                    }
                }
            }
            else {
                send(
                        exchange,
                        405,
                        "application/json",
                        errorJSON(
                                "Only GET and POST are supported."
                        )
                );
                return;
            }
            data = prepareData(data);
            validateDataset(data);
            String result =
                    analyzeEngineDataset(data);
            send(
                    exchange,
                    200,
                    "application/json",
                    result
            );
        } catch (Exception e) {
            e.printStackTrace();
            String message =
                    e.getMessage();
            if (message == null ||
                    message.isBlank()) {
                message = "Analysis failed.";
            }
            System.out.println();
            System.out.println(
                    "[ERROR] " + message
            );
            send(
                    exchange,
                    400,
                    "application/json",
                    errorJSON(message)
            );
        }
    }
    static Instances loadCSV(Path file)
            throws Exception {
        CSVLoader loader =
                new CSVLoader();
        loader.setSource(file.toFile());
        Instances data =
                loader.getDataSet();
        if (data == null) {
            throw new IllegalArgumentException(
                    "Unable to read CSV dataset."
            );
        }
        return data;
    }
    static Instances loadCSVFromText(String text)
            throws Exception {
        Path temp =
                Files.createTempFile(
                        "deviora-upload-",
                        ".csv"
                );
        Files.writeString(
                temp,
                text,
                StandardCharsets.UTF_8
        );
        temp.toFile().deleteOnExit();
        CSVLoader loader =
                new CSVLoader();
        loader.setSource(
                temp.toFile()
        );
        Instances data =
                loader.getDataSet();
        if (data == null) {
            throw new IllegalArgumentException(
                    "Unable to parse uploaded CSV."
            );
        }
        return data;
    }
    static Instances prepareData(
            Instances original
    ) throws Exception {
        if (original == null) {
            throw new IllegalArgumentException(
                    "Dataset is empty."
            );
        }
        Instances data =
                new Instances(original);
        ArrayList<Integer> stringIndexes =
                new ArrayList<>();
        for (int i = 0;
             i < data.numAttributes();
             i++) {
            if (data.attribute(i).isString()) {
                stringIndexes.add(i + 1);
            }
        }
        if (!stringIndexes.isEmpty()) {
            StringBuilder range =
                    new StringBuilder();
            for (int i = 0;
                 i < stringIndexes.size();
                 i++) {
                if (i > 0) {
                    range.append(",");
                }
                range.append(
                        stringIndexes.get(i)
                );
            }
            StringToNominal filter =
                    new StringToNominal();
            filter.setAttributeRange(
                    range.toString()
            );
            filter.setInputFormat(data);
            data =
                    Filter.useFilter(
                            data,
                            filter
                    );
        }
        return data;
    }
    static void validateDataset(
            Instances data
    ) {
        if (data.numInstances() == 0) {
            throw new IllegalArgumentException(
                    "No records found in the CSV."
            );
        }
        if (data.numAttributes() < 2) {

            throw new IllegalArgumentException(
                    "CSV must contain input columns and a target column."
            );
        }
        String[] required = {
                "Engine_ID",
                "Temperature_C",
                "Oil_Pressure_bar",
                "RPM",
                "Vibration_mm_s",
                "Torque_Nm",
                "Load_Percent",
                "Runtime_Hours",
                "Coolant_Temp_C",
                "Fuel_Pressure_bar",
                "Quality_Status"
        };
        ArrayList<String> missing =
                new ArrayList<>();
        for (String name : required) {
            if (data.attribute(name) == null) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Required column(s) missing: "
                            + String.join(
                            ", ",
                            missing
                    )
            );
        }
        Attribute target =
                data.attribute("Quality_Status");
        if (!target.isNominal()) {
            throw new IllegalArgumentException(
                    "Quality_Status must contain categorical values such as PASS and FAIL."
            );
        }
        if (target.numValues() < 2) {
            throw new IllegalArgumentException(
                    "Quality_Status must contain at least two classes."
            );
        }
        int labelledRows = 0;
        boolean[] observedClasses = new boolean[target.numValues()];
        for (int i = 0; i < data.numInstances(); i++) {
            Instance row = data.instance(i);
            if (row.isMissing(target)) {
                throw new IllegalArgumentException(
                        "Quality_Status has a missing value at CSV data row " + (i + 2) + "."
                );
            }
            int classValue = (int) row.value(target.index());
            if (classValue >= 0 && classValue < observedClasses.length) {
                observedClasses[classValue] = true;
                labelledRows++;
            }
        }
        int observedClassCount = 0;
        for (boolean observed : observedClasses) {
            if (observed) observedClassCount++;
        }
        if (labelledRows == 0 || observedClassCount < 2) {
            throw new IllegalArgumentException(
                    "Quality_Status must contain at least two observed classes (for example, PASS and FAIL)."
            );
        }
        if (data.numInstances() < 10) {
            throw new IllegalArgumentException(
                    "At least 10 records are required for analysis."
            );
        }
    }
    static String analyzeEngineDataset(
            Instances data
    ) throws Exception {
        Attribute target =
                data.attribute("Quality_Status");
        int classIndex =
                target.index();
        data.setClassIndex(classIndex);
        List<Integer> featureIndexes =
                new ArrayList<>();
        for (int i = 0;
             i < data.numAttributes();
             i++) {
            if (i == classIndex) {
                continue;
            }
            Attribute attribute =
                    data.attribute(i);
            if (attribute.name()
                    .equalsIgnoreCase("Engine_ID")) {
                continue;
            }
            if (attribute.isNumeric() ||
                    attribute.isNominal()) {
                featureIndexes.add(i);
            }
        }
        if (featureIndexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "No usable machine-learning features were found."
            );
        }
        Instances modelData =
                selectFeatures(
                        data,
                        featureIndexes,
                        classIndex
                );
        modelData.setClassIndex(
                modelData.numAttributes() - 1
        );
        RandomForest model =
                new RandomForest();
        model.setNumIterations(300);
        model.setSeed(42);
        model.setMaxDepth(0);
        model.setNumFeatures(0);
        int n = modelData.numInstances();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "At least 2 valid records are required for training and testing."
            );
        }
        // Randomize first, then stratify because Quality_Status is nominal.
        modelData.randomize(new Random(42));
        int folds = Math.min(5, n);
        modelData.stratify(folds);
        Instances train = modelData.trainCV(folds, 0);
        Instances test = modelData.testCV(folds, 0);
        if (train.numInstances() == 0 || test.numInstances() == 0) {
            throw new IllegalArgumentException(
                    "Not enough records to create training and testing sets."
            );
        }
        model.buildClassifier(train);
        double accuracy =
                test.numInstances() > 0
                        ? accuracy(model, test)
                        : 0;
        double[] importance =
                permutationImportance(
                        model,
                        test,
                        featureIndexes.size()
                );
        normalize(importance);
        int pass = 0;
        int fail = 0;
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        Map<String, Integer> signatures =
                new LinkedHashMap<>();
        signatures.put(
                "THERMAL OVERLOAD",
                0
        );
        signatures.put(
                "LUBRICATION PRESSURE",
                0
        );
        signatures.put(
                "VIBRATION BEARING",
                0
        );
        signatures.put(
                "OVERLOAD RPM",
                0
        );
        signatures.put(
                "FUEL PRESSURE",
                0
        );
        signatures.put(
                "THERMAL VIBRATION",
                0
        );
        signatures.put(
                "WEAR MAINTENANCE",
                0
        );
        signatures.put(
                "GENERAL FAILURE",
                0
        );
        List<String> engines =
                new ArrayList<>();
        for (int i = 0;
             i < data.numInstances();
             i++) {
            Instance source =
                    data.instance(i);
            Instance modelInstance =
                    projectInstance(
                            source,
                            modelData,
                            featureIndexes
                    );
            double prediction =
                    model.classifyInstance(
                            modelInstance
                    );
            int predictionIndex =
                    (int) prediction;
            String predictedLabel =
                    modelData
                            .classAttribute()
                            .value(
                                    predictionIndex
                            );
            double[] distribution =
                    model.distributionForInstance(
                            modelInstance
                    );
            double confidence = 0;
            if (predictionIndex >= 0 &&
                    predictionIndex < distribution.length) {
                confidence =
                        distribution[predictionIndex]
                                * 100.0;
            }
            String actual =
                    safeClassValue(
                            source,
                            classIndex,
                            data
                    );
            boolean predictedFail =
                    isFailureLabel(
                            predictedLabel,
                            target
                    );
            List<Factor> factors =
                    rowFactors(
                            source,
                            data,
                            featureIndexes,
                            importance
                    );
            factors.sort(
                    (a, b) ->
                            Double.compare(
                                    b.score,
                                    a.score
                            )
            );
            if (factors.size() > 5) {
                factors =
                        new ArrayList<>(
                                factors.subList(
                                        0,
                                        5
                                )
                        );
            }
            String signature =
                    detectSignature(
                            source,
                            data,
                            predictedFail
                    );
            if (predictedFail) {
                fail++;
                signatures.put(
                        signature,
                        signatures.getOrDefault(
                                signature,
                                0
                        ) + 1
                );
            } else {
                pass++;
            }
            int risk =
                    calculateEngineRisk(
                            predictedFail,
                            confidence,
                            factors,
                            source,
                            data
                    ); 
            String priority =
                    priority(risk);
            if (predictedFail) {
                if (priority.equals("CRITICAL")) {
                    critical++;
                } else if (priority.equals("HIGH")) {
                    high++;
                } else if (priority.equals("MEDIUM")) {
                    medium++;
                } else {
                    low++;
                }
            }
            String engineId =
                    getEngineId(
                            source,
                            data,
                            i + 1
                    );
            List<String> recommendations =
                    recommendations(
                            signature,
                            priority
                    ); 
            engines.add(
                    engineJSON(
                            engineId,
                            predictedLabel,
                            actual,
                            confidence,
                            signature,
                            priority,
                            risk,
                            factors,
                            source,
                            data,
                            recommendations
                    )
            );
        }
        StringBuilder importanceJSON =
                new StringBuilder("{");
        for (int i = 0;
             i < featureIndexes.size();
             i++) {
            if (i > 0) {
                importanceJSON.append(",");
            }
            String name =
                    data.attribute(
                            featureIndexes.get(i)
                    ).name();
            importanceJSON
                    .append("\"")
                    .append(
                            jsonEscape(name)
                    )
                    .append("\":")
                    .append(
                            round(
                                    importance[i]
                            )
                    );
        }
        importanceJSON.append("}");
        StringBuilder output =
                new StringBuilder();
        output.append("{");
        output.append(
                "\"project\":\"Intelligent Failure Prediction and Risk Analysis Using Machine Learning\","
        );
        output.append(
                "\"status\":\"success\","
        );
        output.append(
                "\"total\":"
        ).append(
                data.numInstances()
        ).append(",");
        output.append(
                "\"pass\":"
        ).append(pass)
                .append(",");
        output.append(
                "\"fail\":"
        ).append(fail)
                .append(",");
        output.append(
                "\"critical\":"
        ).append(critical)
                .append(",");
        output.append(
                "\"high\":"
        ).append(high)
                .append(",");
        output.append(
                "\"medium\":"
        ).append(medium)
                .append(",");
        output.append(
                "\"low\":"
        ).append(low)
                .append(",");
        output.append(
                "\"accuracy\":"
        ).append(
                round(
                        accuracy * 100
                )
        ).append(",");
        output.append(
                "\"target\":\"Quality_Status\","
        );
        output.append(
                "\"signatures\":"
        ).append(
                mapJson(signatures)
        ).append(",");
        output.append(
                "\"featureImportance\":"
        ).append(
                importanceJSON
        ).append(",");
        output.append(
                "\"engines\":["
        );
        output.append(
                String.join(
                        ",",
                        engines
                )
        );
        output.append("]");
        output.append("}");
        System.out.println();
        System.out.println(
                "[SUCCESS] Analysis completed."
        );
        System.out.println(
                "Records : " + data.numInstances()
        );
        System.out.println(
                "PASS    : " + pass
        );
        System.out.println(
                "FAIL    : " + fail
        );
        System.out.println(
                "Accuracy: "
                        + round(accuracy * 100)
                        + "%"
        );
        return output.toString();
    }
    static Instances selectFeatures(
            Instances data,
            List<Integer> features,
            int classIndex
    ) {
        ArrayList<Attribute> attributes =
                new ArrayList<>();
        for (int index : features) {
            attributes.add(
                    (Attribute)
                            data.attribute(index)
                                    .copy()
            );
        }
        attributes.add(
                (Attribute)
                        data.classAttribute()
                                .copy()
        );
        Instances result =
                new Instances(
                        "DEVIORA_ModelData",
                        attributes,
                        data.numInstances()
                );
        for (int r = 0;
             r < data.numInstances();
             r++) {
            Instance original =
                    data.instance(r);
            DenseInstance instance =
                    new DenseInstance(
                            attributes.size()
                    );
            instance.setDataset(result);
            int position = 0;
            for (int index : features) {
                if (original.isMissing(index)) {
                    instance.setMissing(
                            position
                    );
                } else {
                    instance.setValue(
                            position,
                            original.value(index)
                    );
                }
                position++;
            }
            if (original.isMissing(classIndex)) {
                instance.setMissing(
                        position
                );
            } else {
                instance.setValue(
                        position,
                        original.value(classIndex)
                );
            }
            result.add(instance);
        }
        result.setClassIndex(
                result.numAttributes() - 1
        );
        return result;
    }
    static Instance projectInstance(
            Instance source,
            Instances modelData,
            List<Integer> features
    ) {
        DenseInstance instance =
                new DenseInstance(
                        modelData.numAttributes()
                );
        instance.setDataset(modelData);
        int position = 0;
        for (int index : features) {
            if (source.isMissing(index)) {
                instance.setMissing(position);
            } else {
                instance.setValue(
                        position,
                        source.value(index)
                );
            }
            position++;
        }
        instance.setMissing(position);
        return instance;
    }
    static double accuracy(
            RandomForest model,
            Instances test
    ) throws Exception {
        if (test.numInstances() == 0) {
            return 0;
        } 
        int correct = 0;
        for (int i = 0;
             i < test.numInstances();
             i++) {
            Instance instance =
                    test.instance(i);
            double prediction =
                    model.classifyInstance(
                            instance
                    );
            if (prediction ==
                    instance.classValue()) {
                correct++;
            }
        }
        return (double) correct /
                test.numInstances();
    }
    static double[] permutationImportance(
            RandomForest model,
            Instances test,
            int featureCount
    ) throws Exception {
        double[] result =
                new double[featureCount];
        if (test.numInstances() == 0) {
            return result;
        }
        double base =
                accuracy(
                        model,
                        test
                );
        Random random =
                new Random(1234);
        for (int feature = 0;
             feature < featureCount;
             feature++) {
            Instances shuffled =
                    new Instances(test);
            double[] values =
                    new double[
                            shuffled.numInstances()
                    ];
            for (int row = 0;
                 row < shuffled.numInstances();
                 row++) {
                values[row] =
                        shuffled
                                .instance(row)
                                .value(feature);
            }
            for (int i =
                     values.length - 1;
                 i > 0;
                 i--) {
                int j = random.nextInt(i + 1);
                double temp = values[i];
                values[i] = values[j];
                values[j] = temp;
            }
            for (int row = 0;row < shuffled.numInstances();
                 row++) {
                shuffled
                        .instance(row)
                        .setValue(
                                feature,
                                values[row]
                        );
            }
            double shuffledAccuracy =
                    accuracy(
                            model,
                            shuffled
                    );
            result[feature] =
                    Math.max(
                            0,
                            base - shuffledAccuracy
                    );
        }
        return result;
    }
    static void normalize(
            double[] values
    ) {
        double max = 0;
        for (double value : values) {
            max =
                    Math.max(
                            max,
                            value
                    );
        }
        if (max == 0) {
            Arrays.fill(
                    values,
                    100.0 /
                            Math.max(
                                    1,
                                    values.length
                            )
            );
            return;
        }
        for (int i = 0;
             i < values.length;
             i++) {
            values[i] =
                    values[i] /
                            max *
                            100.0;
        }
    }
    static List<Factor> rowFactors(
            Instance row,
            Instances data,
            List<Integer> features,
            double[] importance
    ) {
        List<Factor> factors =
                new ArrayList<>();
        for (int i = 0;
             i < features.size();
             i++) {
            int index =
                    features.get(i);
            Attribute attribute =
                    data.attribute(index);
            if (row.isMissing(index)) {
                continue;
            }
            double anomaly = 0;
            if (attribute.isNumeric()) {
                double mean = 0;
                int count = 0;
                for (int r = 0;
                     r < data.numInstances();
                     r++) {
                    Instance current =
                            data.instance(r);
                    if (!current.isMissing(index)) {
                        mean +=
                                current.value(index);
                        count++;
                    }
                }
                if (count == 0) {
                    continue;
                }
                mean /= count;
                double variance = 0;
                for (int r = 0;
                     r < data.numInstances();
                     r++) {
                    Instance current =
                            data.instance(r);
                    if (!current.isMissing(index)) {
                        double difference =
                                current.value(index)
                                        - mean;
                        variance +=
                                difference *
                                        difference;
                    }
                }
                double sd =
                        Math.sqrt(
                                variance /
                                        Math.max(
                                                1,
                                                count - 1
                                        )
                        );
                if (sd > 0) {
                    anomaly =
                            Math.min(
                                    100,
                                    Math.abs(
                                            row.value(index)
                                                    - mean
                                    )
                                            /
                                            (2.5 * sd)
                                            * 100
                            );
                }
            } else {
                int frequency = 0;
                for (int r = 0;
                     r < data.numInstances();
                     r++) {
                    Instance current =
                            data.instance(r); 
                    if (!current.isMissing(index) &&
                            current.value(index)
                                    == row.value(index)) {

                        frequency++;
                    }
                }
                anomaly =
                        100.0 *
                                (
                                        1.0 -
                                                (
                                                        (double) frequency /
                                                                data.numInstances()
                                                )
                                );
            }
            double score =
                    importance[i] * 0.65 +
                            anomaly * 0.35;
            factors.add(
                    new Factor(
                            attribute.name(),
                            score,
                            importance[i],
                            anomaly,
                            valueText(
                                    row,
                                    attribute
                            )
                    )
            );
        }
        return factors;
    }
    static int calculateEngineRisk(
            boolean failure,
            double confidence,
            List<Factor> factors,
            Instance row,
            Instances data
    ) {
        if (!failure) {

            return Math.max(
                    5,
                    Math.min(
                            44,
                            (int)
                                    Math.round(
                                            confidence *
                                                    0.30
                                    )
                    )
            );
        }

        double strongest =
                factors.isEmpty()
                        ? 0
                        : factors.get(0).score;

        double thresholdRisk =
                thresholdRisk(
                        row,
                        data
                );

        double score =
                confidence * 0.45 +
                        strongest * 0.30 +
                        thresholdRisk * 0.25;

        return Math.max(
                1,
                Math.min(
                        99,
                        (int)
                                Math.round(score)
                )
        );
    }
    static double thresholdRisk(
            Instance row,
            Instances data
    ) {
        double score = 0;
        int checks = 0;

        Double temperature =
                numericValue(
                        row,
                        data,
                        "Temperature_C"
                );
        if (temperature != null) {

            checks++;

            if (temperature >= 105) {
                score += 100;
            } else if (temperature >= 98) {
                score += 80;
            } else if (temperature >= 95) {
                score += 50;
            }
        }
        Double oil =
                numericValue(
                        row,
                        data,
                        "Oil_Pressure_bar"
                );
        if (oil != null) {
            checks++;
            if (oil <= 2.5) {
                score += 100;
            } else if (oil <= 3.2) {
                score += 75;
            } else if (oil <= 4.0) {
                score += 40;
            }
        }
        Double vibration =
                numericValue(
                        row,
                        data,
                        "Vibration_mm_s"
                );
        if (vibration != null) {
            checks++;
            if (vibration >= 5.5) {
                score += 100;
            } else if (vibration >= 4.0) {
                score += 75;
            } else if (vibration >= 3.0) {
                score += 40;
            }
        }
        Double rpm =
                numericValue(
                        row,
                        data,
                        "RPM"
                );
        if (rpm != null) {
            checks++;
            if (rpm >= 3800 ||
                    rpm <= 1800) {
                score += 100;
            } else if (rpm >= 3500 ||
                    rpm <= 2100) {
                score += 75;
            } else if (rpm >= 3250 ||
                    rpm <= 2300) {
                score += 40;
            }
        }
        Double fuel =
                numericValue(
                        row,
                        data,
                        "Fuel_Pressure_bar"
                );
        if (fuel != null) {
            checks++;
            if (fuel <= 2.0) {
                score += 100;
            } else if (fuel <= 3.0) {
                score += 75;
            } else if (fuel <= 3.8) {
                score += 40;
            }
        }
        Double load =
                numericValue(
                        row,
                        data,
                        "Load_Percent"
                );
        if (load != null) { 
            checks++;
            if (load >= 95) {
                score += 100;
            } else if (load >= 88) {
                score += 75;
            } else if (load >= 82) {
                score += 40;
            }
        }
        if (checks == 0) {
            return 0;
        }                      
        return Math.min(
                100,
                score / checks
        );
    }
    static String detectSignature(
            Instance row,
            Instances data,
            boolean failure
    ) {
        if (!failure) {
            return "NORMAL";
        }
        Double temperature =
                numericValue(
                        row,
                        data,
                        "Temperature_C"
                );
        Double oil =
                numericValue(
                        row,
                        data,
                        "Oil_Pressure_bar"
                );
        Double vibration =
                numericValue(
                        row,
                        data,
                        "Vibration_mm_s"
                );
        Double rpm =
                numericValue(
                        row,
                        data,
                        "RPM"
                );
        Double fuel =
                numericValue(
                        row,
                        data,
                        "Fuel_Pressure_bar"
                );
        Double load =
                numericValue(
                        row,
                        data,
                        "Load_Percent"
                );
        Double runtime =
                numericValue(
                        row,
                        data,
                        "Runtime_Hours"
                );
        if (temperature != null &&
                temperature >= 105) {

            return "THERMAL OVERLOAD";
        }
        if (oil != null &&
                oil <= 2.5) {

            return "LUBRICATION PRESSURE";
        }
        if (vibration != null &&
                vibration >= 5.5) {

            return "VIBRATION BEARING";
        }
        if (rpm != null &&
                (rpm >= 3800 ||
                        rpm <= 1800)) {

            return "OVERLOAD RPM";
        }
        if (fuel != null &&
                fuel <= 2.0) {

            return "FUEL PRESSURE";
        }
        if (temperature != null &&
                vibration != null &&
                temperature >= 98 &&
                vibration >= 4.0) {

            return "THERMAL VIBRATION";
        }
        if (runtime != null &&
                runtime >= 850) {

            return "WEAR MAINTENANCE";
        }
        if (load != null &&
                load >= 95) {

            return "OVERLOAD RPM";
        }
        return "GENERAL FAILURE";
    }
    static String priority(
            int risk
    ) { 
        if (risk >= 85) {
            return "CRITICAL";
        }
        if (risk >= 70) {
            return "HIGH";
        }
        if (risk >= 45) {
            return "MEDIUM";
        }
        return "LOW";
    }
    static boolean isFailureLabel(
            String label,
            Attribute classAttribute
    ) {
        if (label == null) {
            return false;
        }
        String value =
                label.toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^a-z]",
                        ""
                );
        if (value.contains("fail") ||
                value.contains("fault") ||
                value.contains("defect") ||
                value.contains("error") ||
                value.contains("bad") ||
                value.contains("risk")) {
            return true;
        }
        if (value.equals("1") ||
                value.equals("yes") ||
                value.equals("true")) {
            return true;
        }
        if (classAttribute != null &&
                classAttribute.numValues() == 2) 
         {
            String other;
            if (classAttribute
                    .value(0)
                    .equals(label)) {
                other =
                        classAttribute.value(1);
            } else {
                other =
                        classAttribute.value(0);
            }
            String otherValue =
                    other.toLowerCase(
                            Locale.ROOT
                    )
                    .replaceAll(
                            "[^a-z]",
                            ""
                    );
            if (otherValue.contains("pass") ||
                    otherValue.contains("good") ||
                    otherValue.contains("normal") ||
                    otherValue.contains("healthy") ||
                    otherValue.equals("no") ||
                    otherValue.equals("false")) {
                return true;
            }
        }
        return false;
    }          
    static String engineJSON(
            String id,
            String prediction,
            String actual,
            double confidence,
            String signature,
            String priority,
            int risk,
            List<Factor> factors,
            Instance row,
            Instances data,
            List<String> recommendations
    ) {
        StringBuilder json =
                new StringBuilder();
        json.append("{");
        json.append(
                "\"id\":\""
        ).append(
                jsonEscape(id)
        ).append("\",");
        json.append(
                "\"prediction\":\""
        ).append(
                jsonEscape(prediction)
        ).append("\",");
        json.append(
                "\"actual\":\""
        ).append(
                jsonEscape(actual)
        ).append("\",");
        json.append(
                "\"confidence\":"
        ).append(
                round(confidence)
        ).append(",");
        json.append(
                "\"signature\":\""
        ).append(
                jsonEscape(signature)
        ).append("\",");
        json.append(
                "\"priority\":\""
        ).append(
                jsonEscape(priority)
        ).append("\",");
        json.append(
                "\"riskScore\":"
        ).append(risk)
                .append(",");
        json.append("\"factors\":[");
        for (int i = 0;
             i < factors.size();
             i++) {
            if (i > 0) {
                json.append(",");
            }
            Factor factor =
                    factors.get(i);
            String type =
                    factorType(
                            factor.name
                    );
            json.append("{");
            json.append(
                    "\"name\":\""
            ).append(
                    jsonEscape(
                            displayFactorName(
                                    factor.name
                            )
                    )
            ).append("\",");
            json.append(
                    "\"type\":\""
            ).append(
                    jsonEscape(type)
            ).append("\",");
            json.append(
                    "\"score\":"
            ).append(
                    round(
                            factor.score
                    )
            ).append(",");
            json.append(
                    "\"importance\":"
            ).append(
                    round(
                            factor.importance
                    )
            ).append(",");
            json.append(
                    "\"anomaly\":"
            ).append(
                    round(
                            factor.anomaly
                    )
            ).append(",");
            json.append(
                    "\"value\":\""
            ).append(
                    jsonEscape(
                            factor.value
                    )
            ).append("\"");
            json.append("}");
        }
        json.append("],");
        json.append("\"values\":{");
        String[] fields = {
                "Temperature_C",
                "Oil_Pressure_bar",
                "RPM",
                "Vibration_mm_s",
                "Torque_Nm",
                "Load_Percent",
                "Runtime_Hours",
                "Coolant_Temp_C",
                "Fuel_Pressure_bar"
        };
        for (int i = 0;
             i < fields.length;
             i++) {
            if (i > 0) {
                json.append(",");
            }
            String field =
                    fields[i];
            Double value =
                    numericValue(
                            row,
                            data,
                            field
                    );
            String jsonName =
                    valueFieldName(
                            field
                    );
            json.append(
                    "\""
            ).append(
                    jsonName
            ).append(
                    "\":"
            );
            if (value == null) {
                json.append("null");
            } else {
                json.append(
                        trimNumber(
                                value
                        )
                );
            }
        }
        json.append("},");
        json.append(
                "\"recommendations\":["
        );
        for (int i = 0;
             i < recommendations.size();
             i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"")
                    .append(
                            jsonEscape(
                                    recommendations.get(i)
                            )
                    )
                    .append("\"");
        }            
        json.append("]");
        json.append("}");
        return json.toString();
    }
    static List<String> recommendations(
            String signature,
            String priority
    ) {
        List<String> list =
                new ArrayList<>();
        if (signature.equals(
                "THERMAL OVERLOAD"
        )) {
            list.add(
                    "Inspect cooling system and coolant circulation."
            );
            list.add(
                    "Check abnormal temperature rise before continued operation."
            );
        } else if (signature.equals(
                "LUBRICATION PRESSURE"
        )) {
            list.add(
                    "Inspect oil level and lubrication pressure."
            );
            list.add(
                    "Check pump, filter and lubrication lines."
            );
        } else if (signature.equals(
                "VIBRATION BEARING"
        )) {           
            list.add(
                    "Inspect bearings, shaft alignment and rotating components."
            );
            list.add(
                    "Check vibration source before further operation."
            );
        } else if (signature.equals(
                "OVERLOAD RPM"
        )) {
            list.add(
                    "Inspect operating load and RPM conditions."
            );
            list.add(
                    "Check whether the engine is operating outside its rated range."
            );
        } else if (signature.equals(
                "FUEL PRESSURE"
        )) {
            list.add(
                    "Inspect fuel pressure and fuel delivery system."
            );
            list.add(
                    "Check fuel filter and pump condition."
            );
        } else if (signature.equals(
                "THERMAL VIBRATION"
        )) {
            list.add(
                    "Inspect cooling and rotating-system conditions together."
            );
            list.add(
                    "Check temperature and vibration trends."
            );
        } else if (signature.equals(
                "WEAR MAINTENANCE"
        )) {
            list.add(
                    "Review maintenance history and accumulated runtime."
            );
            list.add(
                    "Schedule preventive inspection."
            );
        } else {
            list.add(
                    "Perform detailed engine inspection."
            );
            list.add(
                    "Review the highlighted risk factors."
            );
        }
        if (priority.equals("CRITICAL")) {
            list.add(
                    "Prioritize immediate technical inspection."
            );
        } else if (priority.equals("HIGH")) {
            list.add( "Schedule inspection at the earliest opportunity.");
        }
        return list;
    }
    static String factorType(
            String name
    ) {
        String value =
                name.toLowerCase(
                        Locale.ROOT
                );
        if (value.contains("temperature") ||
                value.contains("coolant")) {
            return "THERMAL";
        }
        if (value.contains("pressure")) {
            return "PRESSURE";
        }
        if (value.contains("vibration")) {
            return "VIBRATION";
}                                                                                                                                                               
        if (value.contains("rpm")) {
            return "SPEED";
        }
        if (value.contains("load")) {
            return "LOAD";
        }
        if (value.contains("torque")) {
            return "MECHANICAL";
        }
        if (value.contains("runtime")) {
            return "WEAR";
        }
        return "GENERAL";
    }
    static String displayFactorName(
            String name
    ) {
        switch (name) {
            case "Temperature_C":
                return "High Temperature";
            case "Oil_Pressure_bar":
                return "Low Oil Pressure";
            case "RPM":
                return "Abnormal RPM";
            case "Vibration_mm_s":
                return "High Vibration";
            case "Torque_Nm":
                return "Torque Variation";
            case "Load_Percent":
                return "High Load";
            case "Runtime_Hours":
                return "Extended Runtime";
            case "Coolant_Temp_C":
                return "Coolant Temperature";
            case "Fuel_Pressure_bar":
                return "Fuel Pressure";
            default:
                return name;
        }
    }
    static String valueFieldName(
            String name
    ) {
        switch (name) {
            case "Temperature_C":
                return "temperature";
            case "Oil_Pressure_bar":
                return "oilPressure";
            case "RPM":
                return "rpm";
            case "Vibration_mm_s":
                return "vibration";
            case "Torque_Nm":
                return "torque";
            case "Load_Percent":
                return "load";
            case "Runtime_Hours":
                return "runtime";
            case "Coolant_Temp_C":
                return "coolant";
            case "Fuel_Pressure_bar":
                return "fuelPressure";
            default:
                return name;
        }
    }
    static class Factor {
        String name;
        double score;
        double importance;
        double anomaly;
        String value;
        Factor(
                String name,
                double score,
                double importance,
                double anomaly,
                String value
        ) {
            this.name = name;
            this.score = score;
            this.importance = importance;
            this.anomaly = anomaly;
            this.value = value;
        }
    }
    static Double numericValue(
            Instance row,
            Instances data,
            String attributeName
    ) {
        Attribute attribute =
                data.attribute(
                        attributeName
                );
        if (attribute == null) {
            return null;
        }
        int index =
                attribute.index();
        if (row.isMissing(index)) {
            return null;
        }
        if (!attribute.isNumeric()) {
            return null;
        }
        return row.value(index);
    }
    static String getEngineId(
            Instance row,
            Instances data,
            int fallback
    ) {
        Attribute attribute =
                data.attribute("Engine_ID");
        if (attribute == null) {
            return String.valueOf(
                    fallback
            );
        }
        int index =
                attribute.index();
        if (row.isMissing(index)) {
            return String.valueOf(
                    fallback
            );
        }
        if (attribute.isNumeric()) {
            return trimNumber(
                    row.value(index)
            );
        }
        if (attribute.isNominal()) {
            return attribute.value(
                    (int) row.value(index)
            );
        }
        return String.valueOf(
                fallback
        );
    }
    static String safeClassValue(
            Instance row,
            int classIndex,
            Instances data
    ) {
        if (row.isMissing(classIndex)) {
            return "UNKNOWN";
        }
        Attribute attribute =
                data.attribute(classIndex);
        if (attribute.isNominal()) {
            return attribute.value(
                    (int) row.value(classIndex)
            );
        }
        return row.stringValue(
                classIndex
        );
    }
    static String valueText(
            Instance row,
            Attribute attribute
    ) {
        if (row.isMissing(attribute)) {
            return "Missing";
        }
        if (attribute.isNumeric()) {
            return trimNumber(
                    row.value(
                            attribute.index()
                    )
            );
        }
        if (attribute.isNominal()) {
            return attribute.value(
                    (int)
                            row.value(
                                    attribute.index()
                            )
            );
        }
        return row.stringValue(
                attribute.index()
        );
    }
    static String mapJson(
            Map<String, Integer> map
    ) {
        StringBuilder result =
                new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Integer> entry :
                map.entrySet()) {
            if (count++ > 0) {
                result.append(","); 
            }
            result.append("\"")
                    .append(
                            jsonEscape(
                                    entry.getKey()
                            )
                    )
                    .append("\":")
                    .append(
                            entry.getValue()
                    );
        }
        result.append("}");
        return result.toString();
    }
    static String errorJSON(
            String message
    ) {
        return "{"
                + "\"status\":\"error\","
                + "\"error\":\""
                + jsonEscape(message)
                + "\","
                + "\"total\":0,"
                + "\"pass\":0,"
                + "\"fail\":0,"
                + "\"critical\":0,"
                + "\"high\":0,"
                + "\"medium\":0,"
                + "\"low\":0,"
                + "\"accuracy\":0,"
                + "\"signatures\":{},"
                + "\"featureImportance\":{},"
                + "\"engines\":[]"
                + "}";
    }
    static String jsonEscape(
            String value
    ) {
        if (value == null) {
            return "";
        }
        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }
    static String trimNumber(
            double value
    ) {
        if (Double.isNaN(value) ||
                Double.isInfinite(value)) {
            return "0";
        }
        if (Math.rint(value) == value) {

            return String.valueOf(
                    (long) value
            );
        }
        return String.format(
                Locale.US,
                "%.3f",
                value
        );
    }
    static long round(
            double value
    ) {
        if (Double.isNaN(value) ||
                Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value);
    }
    static String getQuery(
            String query,
            String key
    ) {
        if (query == null ||
                query.isBlank()) {
            return null;
        }
        for (String part :
                query.split("&")) {
            String[] pair =
                    part.split(
                            "=",
                            2
                    );
            if (pair.length != 2) {
                continue;
            }
            String name =
                    URLDecoder.decode(
                            pair[0],
                            StandardCharsets.UTF_8
                    );
            if (name.equals(key)) {
                return URLDecoder.decode(
                        pair[1],
                        StandardCharsets.UTF_8
                );
            }
        }
        return null;
    }
    static boolean looksLikeCSV(
            String text
    ) {
        if (text == null ||
                text.isBlank()) {
            return false;
        }
        String firstLine =
                text.split(
                        "\\R",
                        2
                )[0];
        return firstLine.contains(",");
    }
    static String extractMultipartCSV(
            byte[] body,
            String contentType
    ) {
        try {
            int boundaryStart =
                    contentType.indexOf(
                            "boundary="
                    );
            if (boundaryStart < 0) {
                return null;
            }
            String boundary =
                    contentType.substring(
                            boundaryStart +
                                    "boundary=".length()
                    ).trim();
            if (boundary.startsWith("\"") &&
                    boundary.endsWith("\"")) {
                boundary =
                        boundary.substring(
                                1,
                                boundary.length() - 1
                        );
            }
            String raw =
                    new String(
                            body,
                            StandardCharsets.ISO_8859_1
                    );
            String marker =
                    "--" + boundary;
            String[] parts =
                    raw.split(
                            java.util.regex.Pattern.quote(
                                    marker
                            )
                    );
            for (String part : parts) {
                String lower =
                        part.toLowerCase(
                                Locale.ROOT
                        );
                if (!lower.contains(
                        "content-disposition"
                )) {
                    continue;
                }
                int separator = part.indexOf("\r\n\r\n");
                int separatorLength = 4;
                if (separator < 0) {
                    separator = part.indexOf("\n\n");
                    separatorLength = 2;
                }
                if (separator < 0) {
                    continue;
                }
                String content = part.substring(separator + separatorLength);
                content =
                        content.replaceAll(
                                "\\r?\\n--$",
                                ""
                        );
                if (looksLikeCSV(content)) {
                    return content.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    static void send(
            HttpExchange exchange,
            int code,
            String type,
            String body
    ) throws IOException {
        byte[] bytes =
                body.getBytes(
                        StandardCharsets.UTF_8
                );
        Headers headers =
                exchange.getResponseHeaders();
        headers.set(
                "Content-Type",
                type + "; charset=UTF-8"
        );
        headers.set(
                "Access-Control-Allow-Origin",
                "*"
        );
        headers.set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
        );
        headers.set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );
        exchange.sendResponseHeaders(
                code,
                bytes.length
        );
        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }
}
