import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Controller script to start the Keycloak environment.
 *
 * <h2>Run Keycloak with http</h2>
 * <pre>{@code
 *  java start.java
 * }</pre>
 *
 * <h2>Run Keycloak with https</h2>
 * <pre>{@code
 *  java start.java --https
 * }</pre>
 *
 * <h2>Run Keycloak with https and openldap</h2>
 * <pre>{@code
 *  java start.java --https --openldap
 * }</pre>
 *
 * <h2>Run Keycloak with https, openldap and postgres database</h2>
 * <pre>{@code
 *  java start.java --https --openldap --database=postgres
 * }</pre>
 */
class start {

    static final String HELP_CMD = "--help";

    static final String VERBOSE_OPT = "--verbose";

    static final String CI_OPT = "--ci";
    static final String HTTP_OPT = "--http";
    static final String HTTPS_OPT = "--https";
    static final String PROVISION_OPT = "--provision";
    static final String OPENLDAP_OPT = "--openldap";
    static final String OPA_OPT = "--opa";
    static final String KEYCLOAK_OPT = "--keycloak=keycloak";
    static final String POSTGRES_OPT = "--database=postgres";
    static final String NATS_OPT = "--messaging=nats";

    static final String ORACLE_OPT = "--database=oracle";
    static final String MSSQL_OPT = "--database=mssql";
    static final String MYSQL_OPT = "--database=mysql";
    static final String GRAYLOG_OPT = "--logging=graylog";
    static final String GRAFANA_OPT = "--grafana";
    static final String PROMETHEUS_OPT = "--metrics=prometheus";
    static final String EXTENSIONS_OPT = "--extensions=";
    static final String EXTENSIONS_OPT_CLASSES = "classes";
    static final String EXTENSIONS_OPT_JAR = "jar";
    static final String DETACH_OPT = "--detach";
    static final String TRACING_OPT = "--tracing";
    static final String DOCKER_HOST_OPT = "--docker-host=";

    public static void main(String[] args) throws Exception {

        var argList = Arrays.asList(args);

        var useKeycloakx = !argList.contains(KEYCLOAK_OPT); // --keycloak=keycloakx is implied by default
        var useHttp = !argList.contains(HTTP_OPT + "=false"); // --http is implied by default
        var useHttps = argList.contains(HTTPS_OPT) || argList.contains(HTTPS_OPT + "=true");
        var useProvision = !argList.contains(PROVISION_OPT + "=false");
        var useOpenLdap = argList.contains(OPENLDAP_OPT) || argList.contains(OPENLDAP_OPT + "=true");
        var usePostgres = argList.contains(POSTGRES_OPT);
        var useOpa = argList.contains(OPA_OPT);
        var useMssql = argList.contains(MSSQL_OPT);
        var useMysql = argList.contains(MYSQL_OPT);
        var useOracle = argList.contains(ORACLE_OPT);
        var useDatabase = usePostgres || useMysql || useMssql || useOracle;
        var useGraylog = argList.contains(GRAYLOG_OPT);
        var useGrafana = argList.contains(GRAFANA_OPT);
        var usePrometheus = argList.contains(PROMETHEUS_OPT);
        var extension = argList.stream().filter(s -> s.startsWith(EXTENSIONS_OPT)).map(s -> s.substring(s.indexOf("=") + 1)).findFirst().orElse(EXTENSIONS_OPT_CLASSES);
        var ci = argList.stream().filter(s -> s.startsWith(CI_OPT)).map(s -> s.substring(s.indexOf("=") + 1)).findFirst().orElse(null);
        var useDetach = argList.contains(DETACH_OPT);
        var verbose = argList.contains(VERBOSE_OPT);
        var useTracing = argList.contains(TRACING_OPT);
        var dockerHost = argList.stream().filter(s -> s.startsWith(DOCKER_HOST_OPT)).map(s -> s.substring(s.indexOf("=") + 1)).findFirst();
        var useNats = argList.contains(NATS_OPT);
        var useSaml = true;

        var showHelp = argList.contains(HELP_CMD);
        if (showHelp) {
            showHelp();
            System.exit(0);
            return;
        }

        if (useDatabase && !(useMysql ^ usePostgres ^ useMssql ^ useOracle)) {
            System.out.println("Invalid database configuration detected. Only one --database parameter is allowed!");
            showHelp();
            System.exit(-1);
        }

        // Keycloak
        createFolderIfMissing("deployments/local/dev/run/keycloak/logs");
        createFolderIfMissing("deployments/local/dev/run/keycloak/data");
        createFolderIfMissing("deployments/local/dev/run/keycloak/perf");

        // Keycloak-X
        createFolderIfMissing("deployments/local/dev/run/keycloakx/logs");
        createFolderIfMissing("deployments/local/dev/run/keycloakx/data");
        createFolderIfMissing("deployments/local/dev/run/keycloakx/perf");

        System.out.println("### Starting Keycloak Environment with HTTP" + (useHttps ? "S" : ""));

        System.out.printf("# Keycloak:       %s%n", useHttps ? "https://id.acme.test:8443/auth" : "http://localhost:8080/auth");
        System.out.printf("# MailHog:        %s%n", "http://localhost:1080");
        if (useOpenLdap) {
            System.out.printf("# PhpMyLdapAdmin: %s%n", "http://localhost:17080");
        }

        var envFiles = new ArrayList<String>();
        var requiresBuild = true;

        var commandLine = new ArrayList<String>();
        commandLine.add("docker");
        commandLine.add("compose");
        envFiles.add("keycloak.env");
        envFiles.add("deployments/local/dev/keycloak-common.env");

        commandLine.add("--file");
        commandLine.add("deployments/local/dev/docker-compose.yml");

        commandLine.add("--file");
        if (useKeycloakx) {
            commandLine.add("deployments/local/dev/docker-compose-keycloakx.yml");
        } else {
            commandLine.add("deployments/local/dev/docker-compose-keycloak.yml");
        }

        if ("github".equals(ci)) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-ci-github.yml");
        }

        if (useHttp) {
            envFiles.add("deployments/local/dev/keycloak-http.env");
        }

        if (useHttps) {
            envFiles.remove("deployments/local/dev/keycloak-http.env");

            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-tls.yml");
            envFiles.add("deployments/local/dev/keycloak-tls.env");
        }

        if (useOpenLdap) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-openldap.yml");
            envFiles.add("deployments/local/dev/keycloak-openldap.env");
        }

        if (useOpa) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-opa.yml");
        }

//        if (EXTENSIONS_OPT_CLASSES.equals(extension)) {
//            commandLine.add("--file");
//            commandLine.add("deployments/local/dev/docker-compose-extensions-classes.yml");
//        } else if (EXTENSIONS_OPT_JAR.equals(extension)) {
//            commandLine.add("--file");
//            commandLine.add("deployments/local/dev/docker-compose-extensions-jar.yml");
//        } else {
//            System.err.printf("Unkown extension include option %s, valid ones are %s and %s%n", extension, EXTENSIONS_OPT_CLASSES, EXTENSIONS_OPT_JAR);
//            System.exit(-1);
//        }

        if (usePostgres) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-postgres.yml");
            createFolderIfMissing("deployments/local/dev/run/postgres/data/");
            requiresBuild = true;
        } else if (useMysql) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-mysql.yml");
            createFolderIfMissing("deployments/local/dev/run/mysql/data/");
            requiresBuild = true;
        } else if (useMssql) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-mssql.yml");
            createFolderIfMissing("deployments/local/dev/run/mssql/data/");
            requiresBuild = true;
        } else if (useOracle) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-oracle.yml");
            createFolderIfMissing("deployments/local/dev/run/oracle/data/");
            requiresBuild = true;
        }

        if (useGraylog) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-graylog.yml");
            createFolderIfMissing("deployments/local/dev/run/graylog/data/mongodb");
            requiresBuild = true;
        }

        if (useGrafana) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-grafana.yml");
            createFolderIfMissing("deployments/local/dev/run/grafana");
        }

        if (usePrometheus) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-prometheus.yml");
            createFolderIfMissing("deployments/local/dev/run/prometheus");
        }
        if (useProvision) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-provisioning.yml");
            envFiles.add("deployments/local/dev/keycloak-provisioning.env");
        }

        if (useNats) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-nats.yml");
        }

        if (useTracing) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-tracing.yml");
            if (useHttps) {
                commandLine.add("--file");
                commandLine.add("deployments/local/dev/docker-compose-tracing-tls.yml");

                var certPath = Path.of("config/stage/dev/tls/acme.test+1.pem");
                if (certPath.toFile().exists()) {
                    var targetPath = Path.of("deployments/local/dev/otel-collector").resolve(certPath.getFileName());
                    System.out.printf("Copy cert files for otel-collector from %s to %s%n", certPath, targetPath);
                    Files.copy(certPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                var keyPath = Path.of("config/stage/dev/tls/acme.test+1-key.pem");
                if (keyPath.toFile().exists()) {
                    var targetPath = Path.of("deployments/local/dev/otel-collector").resolve(keyPath.getFileName());
                    System.out.printf("Copy cert files for otel-collector from %s to %s%n", keyPath, targetPath);
                    Files.copy(keyPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            envFiles.add("deployments/local/dev/keycloak-tracing.env");
        }

        if (useSaml) {
            commandLine.add("--file");
            commandLine.add("deployments/local/dev/docker-compose-simplesaml.yml");
        }

        if (Files.exists(Path.of("local.env"))) {
            System.out.println("Adding local.env");
            envFiles.add("local.env");
        }

        //-BEGIN env vars
        StringBuilder envVariables = new StringBuilder();
        for (String envFile : envFiles) {
            envVariables.append(Files.readString(Paths.get(envFile))).append("\n");
        }

        if (useHttps) {
            // add quotes around path in case of spaces in path
            envVariables.append("CA_ROOT_CERT=\"" + getRootCALocation() + "/rootCA.pem\"");
            envVariables.append("\n");
        }

        if (useHttp && useKeycloakx) {
            Path certPath = Path.of("config/stage/dev/tls/acme.test+1.pem");
            if (certPath.toFile().exists()) {
                Path targetPath = Path.of("deployments/local/dev/keycloakx").resolve(certPath.getFileName());
                System.out.printf("Copy cert file for truststore import from %s to %s%n", certPath, targetPath);
                Files.copy(certPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (dockerHost.isPresent()) {
            envVariables.append(String.format("DOCKER_HOST_IP=\"%s\"", dockerHost.get()));
        }

        if (!envVariables.toString().isBlank()) {
            String generatedEnvFile = "generated.env.tmp";
            Files.writeString(Paths.get(generatedEnvFile), envVariables.toString());
            commandLine.add("--env-file");
            commandLine.add(generatedEnvFile);
        }
        //-END env vars

        commandLine.add("up");
        if (useDetach) {
            commandLine.add("--detach");
        }

        if (requiresBuild) {
            commandLine.add("--build");
        }

        commandLine.add("--remove-orphans");

        if (verbose) {
            System.out.printf("Generated command: %n```%n%s%n```%n",
                    commandLine.stream().collect(Collectors.joining(" \\\n")));
        }

        System.exit(runCommandAndWait(commandLine));
    }

    private static void showHelp() {
        System.out.println("Keycloak Environment starter");
        System.out.printf("%n%s supports the following options: %n", "start.java");
        System.out.println("");
        System.out.printf("  %s: %s%n", HTTP_OPT, "enables HTTP support.");
        System.out.printf("  %s: %s%n", HTTPS_OPT, "enables HTTPS support. (Optional) Implies --http. If not provided, plain HTTP is used");
        System.out.printf("  %s: %s%n", PROVISION_OPT, "enables provisioning via keycloak-config-cli.");
        System.out.printf("  %s: %s%n", OPENLDAP_OPT, "enables OpenLDAP support. (Optional)");
        System.out.printf("  %s: %s%n", POSTGRES_OPT, "enables PostgreSQL database support. (Optional) If no other database is provided, H2 database is used");
        System.out.printf("  %s: %s%n", MYSQL_OPT, "enables MySQL database support. (Optional) If no other database is provided, H2 database is used");
        System.out.printf("  %s: %s%n", ORACLE_OPT, "enables Oracle database support. (Optional) If no other database is provided, H2 database is used");
        System.out.printf("  %s: %s%n", GRAYLOG_OPT, "enables Graylog database support. (Optional)");
        System.out.printf("  %s: %s%n", EXTENSIONS_OPT, "choose dynamic extensions extension based on \"classes\" or static based on \"jar\"");
        System.out.printf("  %s: %s%n", DETACH_OPT, "Detached mode: Run containers in the background and prints the container name.. (Optional)");
        System.out.printf("  %s: %s%n", VERBOSE_OPT, "Shows debug information, such as the generated command");
        System.out.printf("  %s: %s%n", DOCKER_HOST_OPT, "Allows configuring of a non-default IP for reaching the docker host from inside the containers, " +
                "which is used for name resolution. This is useful for using WiFi on ICE trains, which use the same network as docker by default. This causes the wifi to not work correctly.");
        System.out.printf("  %s: %s%n", TRACING_OPT, "enables tracing with open-telemetry. Injects the otel agent into Keycloak, starts an otel-collector and jaeger container");
        System.out.printf("  %s: %s%n", PROMETHEUS_OPT, "enables metrics collection to prometheus. Starts a prometheus metrics container");
        System.out.printf("  %s: %s%n", NATS_OPT, "enables nats message broker");

        System.out.printf("%n%s supports the following commands: %n", "start.java");
        System.out.println("");
        System.out.printf("  %s: %s%n", HELP_CMD, "Shows this help message");

        System.out.printf("%n Usage examples: %n");
        System.out.println("");
        System.out.printf("  %s %s%n", "java start.java", "# Start Keycloak Environment with http");
        System.out.printf("  %s %s%n", "java start.java --https", "# Start Keycloak Environment with https");
        System.out.printf("  %s %s%n", "java start.java --https --verbose", "# Start Keycloak Environment with https and print command");
        System.out.printf("  %s %s%n", "java start.java --provision=false", "# Start Keycloak Environment without provisioning");
        System.out.printf("  %s %s%n", "java start.java --https --database=postgres", "# Start Keycloak Environment with PostgreSQL database");
        System.out.printf("  %s %s%n", "java start.java --https --openldap --database=postgres", "# Start Keycloak Environment with PostgreSQL database and OpenLDAP");
        System.out.printf("  %s %s%n", "java start.java --extensions=classes", "# Start Keycloak with extensions mounted from classes folder. Use --extensions=jar to mount the jar file into the container");
        System.out.printf("  %s %s%n", "java start.java --docker-host=172.19.0.1", "# Configure a non-default IP for the docker host.");
    }

    private static int runCommandAndWait(ArrayList<String> commandLine) {
        var pb = new ProcessBuilder(commandLine);
        pb.directory(new File("."));
        // disable docker compose menu in shell
        pb.environment().put("COMPOSE_MENU", "false");
        pb.inheritIO();
        try {
            var process = pb.start();
            return process.waitFor();
        } catch (Exception ex) {
            System.err.printf("Could not run command: %s.", commandLine);
            ex.printStackTrace();
            return 1;
        }
    }

    private static void createFolderIfMissing(String folderPath) {
        var folder = new File(folderPath);
        if (!folder.exists()) {
            System.out.printf("Creating missing %s folder at %s success:%s%n"
                    , folderPath, folder.getAbsolutePath(), folder.mkdirs());
        }
    }

    private static String getRootCALocation() throws IOException {
        Runtime rt = Runtime.getRuntime();
        String[] mkcertCommand = {"mkcert", "-CAROOT"};
        Process proc = rt.exec(mkcertCommand);
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        return stdInput.readLine().replace('\\','/');
    }

}
