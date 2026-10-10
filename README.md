Rohitaggarwal200@gmail.com - Lead Principal engineer
# Interview

A Java 21 Spring Boot REST API with Jenkins CI, Podman containerization, ELK logging, and Prometheus/Grafana monitoring. The current multi-container setup runs with Podman installed directly in Ubuntu WSL2.

## Prerequisites

* Java 21
* Maven
* Windows with WSL 2
* Podman installed directly in Ubuntu WSL2 for the current Compose setup
* A Compose provider, such as `podman-compose` (`podman compose` delegates to it)
* Podman Desktop for the earlier Windows-based workflow, if using that environment

## Set Java 21

For the current Ubuntu WSL2 workflow, verify Java and Maven inside Ubuntu:

```bash
java -version
mvn -version
```

Both should use Java 21. The following PowerShell configuration applies to the earlier Windows workflow.


Update the JDK path for your local system:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java -version
```

## Run locally

```bash
mvn spring-boot:run
```

Call the health endpoint:

```text
GET http://localhost:8080/api/health
```

## Run unit tests

```bash
mvn test
```

## Build the application JAR

```bash
mvn clean package -DskipTests
```

The generated JAR is:

```text
target/interview-0.0.1-SNAPSHOT.jar
```

## Earlier Windows container execution with Podman Desktop

`Dockerfile.java21` packages the application as a Java 21 container image.

> **Note:** Docker Desktop could not be installed because of organization security and web-access restrictions. Podman Desktop is used as a Docker-compatible alternative.

This section records the earlier Windows/Podman Desktop workflow. For the current setup, run Podman directly in Ubuntu WSL2 and use the Compose commands in the observability section; a separate `podman machine` is not required for that workflow.

### Podman machine setup

Configure a rootless Podman machine with user-mode networking:

```bash
podman machine stop
podman machine set --rootful=false --user-mode-networking=true
podman machine start
```

Verify rootless networking:

```bash
podman info --format '{{.Host.Security.Rootless}}'
podman run --rm --network=pasta docker.io/library/hello-world
```

Expected rootless output:

```text
true
```

### Build the Java 21 image

```bash
podman build -t interview-service:java21 -f Dockerfile.java21 .
```

Verify the image:

```bash
podman images
```

### Run the container

```bash
podman run --rm --name interview-service --network=pasta -p 8080:8080 interview-service:java21
```

Validate the containerized application:

```bash
curl.exe http://localhost:8080/api/health
```

To stop the container, press `Ctrl+C`.

## Jenkins CI pipeline

A Jenkins pipeline is configured to retrieve source code from this GitHub repository and validate changes.

### Pipeline workflow

1. Create a feature branch.
2. Commit and push changes to GitHub.
3. Create a pull request.
4. Jenkins retrieves the source code from GitHub.
5. Jenkins executes the Maven build and unit tests.
6. Review the build result before merging the pull request.

### Pipeline validation

The pipeline was intentionally tested with a failing unit test. Jenkins marked the build as failed, confirming that the CI configuration correctly detects test failures.

After correcting the unit test, run the pipeline again and confirm a successful build before merging the change.

## ELK observability configuration

`compose.observability.yaml` defines the application and observability stack. The Elastic Stack components are:

* Elasticsearch
* Kibana
* Logstash
* Filebeat

The Elastic components use version `9.5.4` through `ELASTIC_VERSION`. The stack also includes `interview-service`, Prometheus, and Grafana.

### Download ELK images

Download the images defined in the Compose file:

```bash
podman compose -f compose.observability.yaml pull
```

The following images are downloaded:

```text
docker.elastic.co/elasticsearch/elasticsearch:9.5.4
docker.elastic.co/kibana/kibana:9.5.4
docker.elastic.co/logstash/logstash:9.5.4
docker.elastic.co/beats/filebeat:9.5.4
```

### ELK image-download evidence

![ELK image download](assets/observability/download-elk-images.png)

![ELK images pulling](assets/observability/download-elk-pulling.png)

![ELK images pulled successfully](assets/observability/download-elk-pulled.png)

### Environment history: Windows Desktop versus Ubuntu WSL2

The earlier laptop used the Windows Podman Desktop workflow. Running multiple containers through Compose encountered a `netavark`/`nftables` bridge-network error. A single application container was validated with rootless `pasta` networking; the native Windows ELK installation was used during that phase.

On another laptop, Podman was installed directly inside Ubuntu WSL2. The multi-container Compose stack ran without the earlier networking error. The application, Elasticsearch, Kibana, Logstash, Filebeat, Prometheus, and Grafana containers were observed running, and the Actuator endpoint, Prometheus scraping, and Grafana metrics queries were validated.

This records a working environment change, not a confirmed fix on the original Windows Desktop environment. The earlier report linked [netavark issue #1495](https://github.com/containers/netavark/issues/1495); the link is retained as a troubleshooting reference, without claiming its current status or confirming it as the exact root cause.

## Prometheus and Grafana monitoring

### What each component does

| Component | Role |
| --- | --- |
| Spring Boot Actuator and Micrometer | Expose current application/JVM measurements at `/actuator/prometheus` |
| Prometheus | Fetch those measurements every 15 seconds and store them with timestamps |
| Grafana | Query Prometheus and display saved dashboards |
| Filebeat, Logstash, Elasticsearch, Kibana | Collect, process, store, and search application logs |

Metrics are numeric measurements; they are separate from the log-file pipeline. The JVM metrics below do not measure total container or Windows host memory. Ubuntu host metrics and individual Podman container metrics require additional exporters.

### Maven dependencies and Actuator exposure

The application uses Spring Boot `3.5.0` and Java 21. Add these dependencies inside the existing `pom.xml` `<dependencies>` section; the Spring Boot parent manages their versions:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

For a local Maven run, use `src/main/resources/application.properties`:

```properties
management.endpoints.web.exposure.include=health,prometheus
```

For Compose, the equivalent setting goes alongside the existing logging setting in `interview-service`:

```yaml
environment:
  LOGGING_FILE_NAME: /app/logs/interview.log
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,prometheus"
```

The existing `/api/health` is an application endpoint. `/actuator/health` is Actuator's health endpoint. `/actuator/prometheus` returns metrics text for Prometheus to collect. No additional Java controller is required for these Actuator endpoints.

### Compose configuration

Create the configuration files below before starting the containers. Add these services under the existing `services:` section of `compose.observability.yaml`:

```yaml
  prometheus:
    image: docker.io/prom/prometheus:latest
    restart: unless-stopped
    ports:
      - "127.0.0.1:9090:9090"
    volumes:
      - ./observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro,z
      - prometheus-data:/prometheus
    depends_on:
      - interview-service

  grafana:
    image: docker.io/grafana/grafana:latest
    restart: unless-stopped
    ports:
      - "127.0.0.1:3001:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: "${GRAFANA_ADMIN_PASSWORD}"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./observability/grafana/datasources.yml:/etc/grafana/provisioning/datasources/datasources.yml:ro,z
    depends_on:
      - prometheus
```

Keep a single top-level `volumes:` section:

```yaml
volumes:
  elasticsearch-data:
  prometheus-data:
  grafana-data:
```

All services in this Compose configuration use its default network unless custom networks are declared. Grafana reaches Prometheus by the service name `prometheus`; Prometheus reaches the application by `interview-service`. `depends_on` sets startup ordering, not application readiness.

Add these settings to the existing, untracked `.env` file beside the Compose file:

```dotenv
ELASTIC_VERSION=9.5.4
GRAFANA_ADMIN_PASSWORD=replace-with-your-own-password
```

Commit placeholders in `.env.example`, not actual passwords. Grafana's environment password initializes a new installation; changing it does not reset an existing account in the data volume. The examples use `latest` to match the local setup; pin tested image tags for repeatable builds.

### Prometheus configuration

Create `observability/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: prometheus
    metrics_path: /metrics
    static_configs:
      - targets: ["localhost:9090"]

  - job_name: interview-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["interview-service:8080"]
```

`localhost:9090` refers to Prometheus itself. `interview-service:8080` refers to the application container on the shared network.

If Java runs directly in the same Ubuntu WSL2 environment instead of a container, change only the application target to `host.containers.internal:8080`. This uses Podman's host-access address; verify it is reachable in your networking setup. Start Prometheus with `--no-deps` so Compose does not start a competing application container on port 8080. Restore `interview-service:8080` when returning to container execution.

### Grafana data source configuration

Create `observability/grafana/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

Grafana creates this data source at startup. Inside Grafana's container, `localhost` refers to Grafana itself, so use `http://prometheus:9090` for this connection.

### Start and validate

Run commands inside Ubuntu WSL2 from the project folder. Use the explicit Compose filename consistently, including for logs, to avoid loading a different default file.

```bash
mvn clean verify
podman compose -f compose.observability.yaml up -d --build
podman compose -f compose.observability.yaml ps
```

The Maven build creates the JAR needed when `Dockerfile.java21` copies from `target/`. Stop any locally running application first to free port 8080.

| Check | Address or command | Expected result |
| --- | --- | --- |
| Application health | `curl http://localhost:8080/api/health` | Application health response |
| Actuator health | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` when checks pass |
| Application metrics | `curl http://localhost:8080/actuator/prometheus` | Metric names and numeric values |
| Prometheus targets | <http://localhost:9090/targets> | Application target shows `UP` |
| Prometheus queries | <http://localhost:9090/query> | Queries return collected data |
| Grafana | <http://localhost:3001> | Login with `admin` and the configured password |
| Kibana | <http://localhost:5601> | Discover can search logs once ingestion and the data view are configured |

In Prometheus or Grafana Explore, run:

```promql
up{job="interview-service"}
```

A value of `1` means the most recent scrape succeeded; `0` means it failed. This checks collection, rather than every aspect of application health. For a failed scrape, inspect the target error on the Prometheus targets page.

Heap memory used, in MiB:

```promql
sum(jvm_memory_used_bytes{job="interview-service", area="heap"}) / 1024 / 1024
```

Heap plus non-heap pool memory used, in MiB:

```promql
sum(jvm_memory_used_bytes{job="interview-service"}) / 1024 / 1024
```

These queries assume the current single application instance. For multiple instances, use `sum by (instance) (...)` to keep a separate line per instance. Individual memory pools produce separate lines when using the raw `jvm_memory_used_bytes` metric. Select a 15-minute range; a one-second graph cannot show meaningful changes with 15-second collection.

### Save a Grafana memory dashboard

1. Open Grafana and log in.
2. Open **Connections → Data sources → Prometheus** and test the connection.
3. Open **Dashboards → New → New dashboard → Add visualization** and select Prometheus.
4. Switch the query editor to **Code** and enter the heap-memory query above.
5. Select a **Time series** visualization, title it **Interview Service — Heap Memory**, and set its unit to **MiB** because the query already converts bytes.
6. Return to the dashboard and save it as **Interview Service Monitoring**.
7. Select **Last 15 minutes** and an available refresh interval of **15s**.

Button names can vary with the Grafana image version. Explore is useful for trying queries; save a dashboard panel to retain a chart. Dashboards, users, and settings persist in `grafana-data`.

## Podman Compose cheat sheet

Use `compose.observability.yaml` explicitly. If your actual filename differs, substitute it in every command. `podman compose` delegates to an installed Compose provider such as `podman-compose`; the provider announcement is informational.

### Manage the stack and individual services

| Task | Command |
| --- | --- |
| Show configured service names | `podman compose -f compose.observability.yaml config --services` |
| Start the whole stack in the background | `podman compose -f compose.observability.yaml up -d` |
| Show Compose container status | `podman compose -f compose.observability.yaml ps` |
| Show all running Podman containers | `podman ps` |
| Show running and stopped containers | `podman ps -a` |
| Stop the stack, retaining containers | `podman compose -f compose.observability.yaml stop` |
| Start existing stopped containers | `podman compose -f compose.observability.yaml start` |
| Stop and remove stack containers and its network | `podman compose -f compose.observability.yaml down` |
| Stop only the application | `podman compose -f compose.observability.yaml stop interview-service` |
| Start its existing stopped container | `podman compose -f compose.observability.yaml start interview-service` |
| Restart only the application | `podman compose -f compose.observability.yaml restart interview-service` |
| Create/start only Grafana without dependencies | `podman compose -f compose.observability.yaml up -d --no-deps grafana` |
| Recreate only Prometheus | `podman compose -f compose.observability.yaml up -d --no-deps --force-recreate prometheus` |

`up` creates containers when needed; `start` starts existing stopped containers; `restart` restarts the existing container and does not rebuild its image or apply changed Compose environment settings. `--no-deps` skips dependency startup. The final argument is a Compose **service name**, not a container ID or Prometheus scrape target.

Ordinary `down` preserves the declared named data volumes. **`down -v` deletes these volumes and their stored data**; do not use it for a normal restart. Keep the same Compose project name to reuse the same automatically prefixed volumes. Stopping the application makes its Prometheus target `DOWN` until it returns and a scrape succeeds.

### Rebuild only the Java application

```bash
mvn clean verify
podman compose -f compose.observability.yaml build interview-service
podman compose -f compose.observability.yaml up -d --no-deps --force-recreate interview-service
```

The other running services continue running. The image is named `interview-service:java21`. To verify it:

```bash
podman images interview-service
```

### Inspect and follow logs

```bash
# Recent application console logs
podman compose -f compose.observability.yaml logs --tail=50 interview-service

# Follow logs continuously; Ctrl+C stops viewing, not the service
podman compose -f compose.observability.yaml logs -f --tail=50 interview-service

# Follow Grafana logs
podman compose -f compose.observability.yaml logs -f --tail=50 grafana

# View Filebeat activity and forwarding errors
podman compose -f compose.observability.yaml logs --tail=50 filebeat
```

For a container ID or name from `podman ps`, use the direct command:

```bash
podman logs -f --tail=50 <container-id-or-name>
```

Do not pass a container ID to `podman compose logs`. If Compose reports `missing services [grafana]` while a Grafana container exists, check the selected Compose filename and `config --services` output.

### Enter a container and inspect log files

Open a shell in the running application container (this is not a separate VM login):

```bash
podman compose -f compose.observability.yaml exec interview-service sh
```

Inside that shell:

```sh
ls -l /app/logs
tail -n 50 /app/logs/interview.log
tail -f /app/logs/interview.log
```

Use `Ctrl+C` to stop following, then `exit` to leave the shell. These commands assume the image includes `sh`, `ls`, and `tail`; minimal images may omit them.

Read the same bind-mounted application file directly from Ubuntu:

```bash
tail -n 50 ./logs/interview.log
tail -f ./logs/interview.log
```

View the configuration visible inside Filebeat:

```bash
podman compose -f compose.observability.yaml exec filebeat cat /usr/share/filebeat/filebeat.yml
```

Container console output (`podman logs`) and application file output (`interview.log`) are different destinations. Filebeat reads the mounted application files; its own console logs describe collection and forwarding activity. Search forwarded application logs in Kibana Discover using the data view configured for the Logstash output index.

### Mounting and labels

A mount makes storage outside a container accessible at a path inside it. The form is `source:container-path:options`.

| Example | Type and purpose |
| --- | --- |
| `./logs:/app/logs:z` | Bind mount: project log directory is available inside the application |
| `./logs:/logs:ro,z` | Bind mount: Filebeat reads that same directory |
| `./observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro,z` | Bind mount: a specific configuration file is supplied from the project |
| `prometheus-data:/prometheus` | Named volume: Podman-managed storage for metrics |
| `grafana-data:/var/lib/grafana` | Named volume: Podman-managed storage for dashboards and settings |

Relative bind-mount paths are resolved from the Compose project/file directory in this setup. Create source configuration files before starting the services. Named volume data is outside the container's writable layer and is not written into the image; it survives container replacement when the same volume is reused.

| Option | Meaning |
| --- | --- |
| `ro` | Read-only inside the container |
| `rw` | Read/write; normally the default |
| `z` | On SELinux systems, relabel for sharing among containers |
| `Z` | On SELinux systems, relabel for private container use (containers within one Pod share its SELinux label) |

SELinux labeling is separate from normal Unix file permissions and from read-only access. Ubuntu WSL2 typically does not have SELinux enforcing, so `z`/`Z` are usually unnecessary there. For shared application/Filebeat logs on an enforcing SELinux host, use shared `z` labeling on both mounts. Relabel only intended project paths. These mount suffixes are different from Compose/container metadata `labels:`.

Inspect the actual named volume and its location:

```bash
podman volume ls
podman volume inspect <actual-volume-name>
```

Compose normally prefixes volume names with the project name, for example `ai-engineering-assistant_grafana-data`. Recreating containers does not delete this storage; deleting the volume or the WSL distribution containing it does.

### Configuration changes and troubleshooting

* After editing the mounted `prometheus.yml`, restart Prometheus: `podman compose -f compose.observability.yaml restart prometheus`.
* After editing `datasources.yml`, restart Grafana: `podman compose -f compose.observability.yaml restart grafana`.
* After changing a Compose environment setting or mount, apply it with `up -d --no-deps --force-recreate SERVICE` using the explicit Compose filename.
* A `404` from `/actuator/prometheus` usually means the dependencies or endpoint exposure are missing, or the running image has not been rebuilt.
* When a target is `DOWN`, inspect the Prometheus targets error. Inside the shared network use `interview-service:8080`; for a local Ubuntu process use a verified host-access address.
* Use the service-specific console logs to investigate startup errors. Confirm source files exist and the container can read them before changing permissions.

### Official references

* [Spring Boot 3.5 Actuator endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
* [Spring Boot 3.5 metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html)
* [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
* [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
* [Podman Compose](https://docs.podman.io/en/stable/markdown/podman-compose.1.html)
* [Podman mounts and SELinux options](https://docs.podman.io/en/stable/markdown/podman-run.1.html)

## Issues encountered and resolutions

| Issue                                                   | Cause                                                                         | Resolution                                                                                                      |
| ------------------------------------------------------- | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Docker Desktop installation was blocked                 | Organization security and web-access restrictions                             | Used Podman Desktop as a Docker-compatible local container platform                                             |
| Java 21 runtime image was required                      | The application requires Java 21                                              | Used the Eclipse Temurin Java 21 runtime image                                                                  |
| Container image required the built application artifact | Maven generates the Spring Boot JAR in `target`                               | Copied `target/interview-0.0.1-SNAPSHOT.jar` into the image                                                     |
| Podman rootful network error                            | `netavark` / `nftables` failed with rootful networking                        | Switched to rootless mode and validated single-container execution with `pasta`                                 |
| Earlier multi-container Compose bridge-network error | `netavark` / `nftables` error in the earlier Windows/Podman Desktop environment | On another laptop, Podman installed directly inside Ubuntu WSL2 ran the stack without this error; original environment remains unverified |
| `useradd` build step failed                             | Podman encountered a network error while starting a temporary build container | Simplified the initial Dockerfile; non-root execution can be added after full network validation                |

## Evidence

```text
assets/
  jenkins/
    jenkins-pipeline-config.png
    jenkins-build-failure.png
    jenkins-build-success.png
  podman/
    podman-image-build.png
    podman-images-list.png
    podman-container-running.png
  observability/
    download-elk-images.png
    download-elk-pulling.png
    download-elk-pulled.png
```

Do not include passwords, tokens, Jenkins credentials, or internal URLs in screenshots.

### Jenkins pipeline configuration

![Jenkins pipeline configuration](assets/jenkins/jenkins-pipeline-config.png)

### Jenkins build validation

![Jenkins build result](assets/jenkins/jenkins-build-success.png)

### Podman image build

![Podman image build](assets/podman/podman-image-build.png)

### Podman image verification

![Podman image list](assets/podman/podman-images-list.png)

### Local container execution

![Running container](assets/podman/podman-container-running.png)

## Security

* Store GitHub credentials and tokens in Jenkins Credentials.
* Do not commit passwords, API keys, tokens, or private URLs.
* Use environment variables or Jenkins credentials for environment-specific configuration.

## Current status

* Jenkins CI pipeline configured and validated with a controlled unit-test failure.
* Java 21 application image built and run with Podman.
* All seven application and observability containers observed running in the current Ubuntu WSL2 environment.
* Spring Boot Actuator health and Prometheus endpoints enabled.
* Prometheus application scraping and Grafana metrics queries validated locally.
* Earlier Windows/Podman Desktop multi-container networking issue documented separately; the current Ubuntu WSL2 setup did not reproduce it.
* Kubernetes learning continues separately with Kind and the Nginx workload.

## Kubernetes learning with Kind in Ubuntu WSL2

Kubernetes learning is intentionally being introduced in small, verifiable steps before deploying the Java `interview-service`. The first workload is Nginx, which separates Kubernetes concepts from Spring Boot troubleshooting.

ELK, Prometheus, and Grafana remain outside Kubernetes in this phase. The current observability runtime is Podman Compose inside Ubuntu WSL2; the native Windows ELK runtime belongs to the earlier environment.

### Prerequisites for local Kubernetes

Install and run the following tools inside Ubuntu WSL2:

* Podman
* `kubectl`
* Kind

Podman builds container images and runs the local Kind node container. Kind creates the local Kubernetes cluster. `kubectl` manages Kubernetes workloads inside that cluster.

```text
Podman → builds images and runs the Kind node
Kind → creates the local Kubernetes cluster
kubectl → manages Kubernetes resources in the cluster
```

### Install `kubectl`

```bash
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl gnupg

sudo mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.37/deb/Release.key \
  | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.37/deb/ /' \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubectl

kubectl version --client
```

### Install Kind

```bash
curl -Lo kind https://kind.sigs.k8s.io/dl/v0.33.0/kind-linux-amd64
chmod +x kind
sudo install -m 0755 kind /usr/local/bin/kind
rm kind

kind version
podman version
```

### Create the local cluster

Kind is explicitly configured to use Podman in the current WSL2 terminal session:

```bash
export KIND_EXPERIMENTAL_PROVIDER=podman
```

`KIND_EXPERIMENTAL_PROVIDER` is the exact environment-variable name recognized by Kind. The value `podman` instructs Kind to use Podman instead of auto-detecting another runtime such as Docker.

Create the cluster:

```bash
kind create cluster --name faang-jobs --wait 5m
```

Verify the cluster:

```bash
kind get clusters
kubectl config current-context
kubectl get nodes
kubectl get pods --all-namespaces
kubectl get namespaces
```

Expected local cluster and context:

```text
Cluster: faang-jobs
Context: kind-faang-jobs
Node:    faang-jobs-control-plane
```

The initial cluster has one Kind node. Kubernetes system Pods and application Pods run on this node during the first learning phase.

### Create an application namespace

Application workloads are deployed to a dedicated namespace rather than `kube-system` or `default`.

```bash
kubectl create namespace faang-jobs-dev
kubectl get namespaces
```

`faang-jobs-dev` is a logical Kubernetes workspace for this project. A namespace organizes resources, avoids naming conflicts, and later supports permissions, quotas, and network policies. It is not a physical node boundary: Pods from one namespace can run on one or multiple nodes.

### Nginx first: Kubernetes learning workload

Nginx is used before the Java application to validate image loading, Deployments, Pods, Services, scaling, logs, and self-healing without application-specific complexity.

Download Nginx with Podman:

```bash
podman pull docker.io/library/nginx:alpine
```

Load the image into the Kind node. The image-archive workflow is used because `kind load docker-image` did not detect a locally available Podman image in this WSL2 environment.

```bash
podman save --output nginx-alpine.tar docker.io/library/nginx:alpine
kind load image-archive nginx-alpine.tar --name faang-jobs
```

Verify images cached inside the Kind node:

```bash
podman exec faang-jobs-control-plane crictl images
podman exec faang-jobs-control-plane crictl images | grep nginx
```

Create a single Nginx replica and expose it with a Kubernetes Service:

```bash
kubectl create deployment nginx-test \
  --image=docker.io/library/nginx:alpine \
  --replicas=1 \
  --namespace=faang-jobs-dev

kubectl expose deployment nginx-test \
  --port=80 \
  --target-port=80 \
  --namespace=faang-jobs-dev
```

Verify the resources:

```bash
kubectl get all -n faang-jobs-dev
kubectl get pods -n faang-jobs-dev -o wide
kubectl get services -n faang-jobs-dev -o wide
```

Access the Service locally:

```bash
kubectl port-forward -n faang-jobs-dev service/nginx-test 8081:80
```

In a second terminal:

```bash
curl http://127.0.0.1:8081
```

> **Note:** `kubectl port-forward service/nginx-test ...` selects one backing Pod for the forwarding session. It is useful for local access but does not by itself demonstrate load balancing across all replicas.

### Scale to three replicas

Scale the Deployment to three Nginx Pods:

```bash
kubectl scale deployment nginx-test \
  --replicas=3 \
  --namespace=faang-jobs-dev

kubectl get pods -n faang-jobs-dev -o wide
```

The Deployment maintains the requested replica count. Each Pod has a unique Pod IP, while the `nginx-test` Service provides one stable ClusterIP and routes to ready Pods matching the label `app=nginx-test`.

### View Pod logs

View logs for one Pod:

```bash
kubectl logs nginx-test-<pod-id> -n faang-jobs-dev
```

Follow logs continuously:

```bash
kubectl logs -f nginx-test-<pod-id> -n faang-jobs-dev
```

View logs from all Nginx Pods:

```bash
kubectl logs -n faang-jobs-dev \
  -l app=nginx-test \
  --prefix=true \
  --all-containers=true
```

### Test Pod self-healing

Open one terminal and watch Pod changes:

```bash
kubectl get pods -n faang-jobs-dev -w
```

In a second terminal, delete one specific Pod:

```bash
kubectl delete pod nginx-test-<pod-id> -n faang-jobs-dev
```

The Deployment controller detects that the actual Pod count is lower than the desired replica count and quickly creates a replacement Pod with a new name. This validates Kubernetes self-healing.

Do not use `kubectl delete pod` to stop an application permanently: a Deployment recreates the Pod. To temporarily stop the Nginx workload while retaining the Deployment, Service, and cached image:

```bash
kubectl scale deployment nginx-test \
  --replicas=0 \
  --namespace=faang-jobs-dev
```

Start it again later:

```bash
kubectl scale deployment nginx-test \
  --replicas=1 \
  --namespace=faang-jobs-dev
```

### Next Kubernetes steps

After validating Nginx, deploy `interview-service` into `faang-jobs-dev` using the same image-build and Kind image-loading workflow. The next application-focused steps are:
1. Deploy one Java service replica.
2. Validate the `/api/health` endpoint.
3. Add readiness, liveness, and startup probes.
4. Define CPU and memory requests and limits.
5. Scale to three replicas.
6. Repeat self-healing and Service-routing tests.
7. Keep ELK external to Kubernetes until the core application deployment is understood and stable.
8. Next would Deploy angular app in another namespace


