package com.devpulse.mcp.k8s.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KubernetesService {

    private CoreV1Api coreApi;
    private AppsV1Api appsApi;
    private boolean connected = false;

    @PostConstruct
    public void init() {
        try {
            ApiClient client = Config.defaultClient();
            client.setReadTimeout(10000);
            Configuration.setDefaultApiClient(client);
            coreApi = new CoreV1Api(client);
            appsApi = new AppsV1Api(client);
            connected = true;
            log.info("Kubernetes client initialized successfully");
        } catch (Exception e) {
            log.warn("Kubernetes client initialization failed: {}. Tools will return error messages.", e.getMessage());
            connected = false;
        }
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("Kubernetes 클러스터에 연결되지 않았습니다. kubeconfig를 확인해주세요.");
        }
    }

    public String listPods(String namespace) {
        ensureConnected();
        try {
            V1PodList podList = coreApi.listNamespacedPod(namespace)
                    .execute();

            if (podList.getItems().isEmpty()) {
                return "## Pod 목록 — " + namespace + "\n\n> Pod가 없습니다.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Pod 목록 — ").append(namespace).append("\n\n");
            sb.append("| Pod 이름 | 상태 | Ready | 재시작 | 노드 | 실행 시간 |\n");
            sb.append("|----------|------|-------|--------|------|----------|\n");

            for (V1Pod pod : podList.getItems()) {
                String name = pod.getMetadata().getName();
                String phase = pod.getStatus().getPhase();

                // Ready containers count
                int readyCount = 0;
                int totalCount = 0;
                int restarts = 0;
                if (pod.getStatus().getContainerStatuses() != null) {
                    totalCount = pod.getStatus().getContainerStatuses().size();
                    for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                        if (Boolean.TRUE.equals(cs.getReady())) readyCount++;
                        restarts += cs.getRestartCount();
                    }
                }

                String node = pod.getSpec().getNodeName() != null ? pod.getSpec().getNodeName() : "-";
                String age = formatAge(pod.getMetadata().getCreationTimestamp());

                String statusEmoji = "Running".equals(phase) ? "✅" : "Pending".equals(phase) ? "⏳" : "❌";
                sb.append(String.format("| %s | %s %s | %d/%d | %d | %s | %s |\n",
                        name, statusEmoji, phase, readyCount, totalCount, restarts, node, age));
            }

            sb.append("\n**총 ").append(podList.getItems().size()).append("개 Pod**");
            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Pod 목록 조회", e);
        }
    }

    public String getPodDetails(String namespace, String podName) {
        ensureConnected();
        try {
            V1Pod pod = coreApi.readNamespacedPod(podName, namespace)
                    .execute();

            StringBuilder sb = new StringBuilder();
            sb.append("## Pod 상세 — ").append(podName).append("\n\n");

            // Basic info
            sb.append("### 기본 정보\n");
            sb.append("- **네임스페이스**: ").append(namespace).append("\n");
            sb.append("- **상태**: ").append(pod.getStatus().getPhase()).append("\n");
            sb.append("- **노드**: ").append(pod.getSpec().getNodeName()).append("\n");
            sb.append("- **IP**: ").append(pod.getStatus().getPodIP()).append("\n");
            sb.append("- **생성일**: ").append(pod.getMetadata().getCreationTimestamp()).append("\n");

            // Labels
            if (pod.getMetadata().getLabels() != null && !pod.getMetadata().getLabels().isEmpty()) {
                sb.append("\n### 레이블\n");
                pod.getMetadata().getLabels().forEach((k, v) ->
                        sb.append("- `").append(k).append("`: ").append(v).append("\n"));
            }

            // Containers
            sb.append("\n### 컨테이너\n");
            sb.append("| 이름 | 이미지 | Ready | 재시작 | 상태 |\n");
            sb.append("|------|--------|-------|--------|------|\n");

            if (pod.getStatus().getContainerStatuses() != null) {
                for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                    String state = "Unknown";
                    if (cs.getState() != null) {
                        if (cs.getState().getRunning() != null) state = "Running";
                        else if (cs.getState().getWaiting() != null) state = "Waiting: " + cs.getState().getWaiting().getReason();
                        else if (cs.getState().getTerminated() != null) state = "Terminated: " + cs.getState().getTerminated().getReason();
                    }
                    sb.append(String.format("| %s | %s | %s | %d | %s |\n",
                            cs.getName(), cs.getImage(),
                            Boolean.TRUE.equals(cs.getReady()) ? "✅" : "❌",
                            cs.getRestartCount(), state));
                }
            }

            // Resource requests/limits
            sb.append("\n### 리소스\n");
            if (pod.getSpec().getContainers() != null) {
                for (V1Container container : pod.getSpec().getContainers()) {
                    sb.append("**").append(container.getName()).append("**:\n");
                    if (container.getResources() != null) {
                        if (container.getResources().getRequests() != null) {
                            sb.append("- Requests: ");
                            container.getResources().getRequests().forEach((k, v) ->
                                    sb.append(k).append("=").append(v.toSuffixedString()).append(" "));
                            sb.append("\n");
                        }
                        if (container.getResources().getLimits() != null) {
                            sb.append("- Limits: ");
                            container.getResources().getLimits().forEach((k, v) ->
                                    sb.append(k).append("=").append(v.toSuffixedString()).append(" "));
                            sb.append("\n");
                        }
                    } else {
                        sb.append("- 리소스 제한 없음\n");
                    }
                }
            }

            // Conditions
            if (pod.getStatus().getConditions() != null) {
                sb.append("\n### 조건\n");
                sb.append("| 유형 | 상태 | 메시지 |\n");
                sb.append("|------|------|--------|\n");
                for (V1PodCondition cond : pod.getStatus().getConditions()) {
                    sb.append(String.format("| %s | %s | %s |\n",
                            cond.getType(),
                            "True".equals(cond.getStatus()) ? "✅" : "❌",
                            cond.getMessage() != null ? cond.getMessage() : "-"));
                }
            }

            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Pod 상세 조회", e);
        }
    }

    public String getPodLogs(String namespace, String podName, int tailLines) {
        ensureConnected();
        try {
            String logs = coreApi.readNamespacedPodLog(podName, namespace)
                    .tailLines(tailLines)
                    .execute();

            StringBuilder sb = new StringBuilder();
            sb.append("## Pod 로그 — ").append(podName).append("\n\n");
            sb.append("최근 ").append(tailLines).append("줄:\n\n");
            sb.append("```\n");
            sb.append(logs != null ? logs : "(로그 없음)");
            sb.append("\n```\n");
            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Pod 로그 조회", e);
        }
    }

    public String listDeployments(String namespace) {
        ensureConnected();
        try {
            V1DeploymentList deployList = appsApi.listNamespacedDeployment(namespace)
                    .execute();

            if (deployList.getItems().isEmpty()) {
                return "## Deployment 목록 — " + namespace + "\n\n> Deployment가 없습니다.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Deployment 목록 — ").append(namespace).append("\n\n");
            sb.append("| 이름 | Ready | Up-to-date | Available | 이미지 | 생성일 |\n");
            sb.append("|------|-------|-----------|-----------|--------|--------|\n");

            for (V1Deployment deploy : deployList.getItems()) {
                String name = deploy.getMetadata().getName();
                V1DeploymentStatus status = deploy.getStatus();

                int ready = status.getReadyReplicas() != null ? status.getReadyReplicas() : 0;
                int desired = deploy.getSpec().getReplicas() != null ? deploy.getSpec().getReplicas() : 0;
                int upToDate = status.getUpdatedReplicas() != null ? status.getUpdatedReplicas() : 0;
                int available = status.getAvailableReplicas() != null ? status.getAvailableReplicas() : 0;

                String image = deploy.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(V1Container::getImage)
                        .collect(Collectors.joining(", "));

                String age = formatAge(deploy.getMetadata().getCreationTimestamp());

                String statusIcon = (ready == desired && desired > 0) ? "✅" : "⚠️";
                sb.append(String.format("| %s %s | %d/%d | %d | %d | %s | %s |\n",
                        statusIcon, name, ready, desired, upToDate, available, image, age));
            }

            sb.append("\n**총 ").append(deployList.getItems().size()).append("개 Deployment**");
            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Deployment 목록 조회", e);
        }
    }

    public String scaleDeployment(String namespace, String deploymentName, int replicas) {
        ensureConnected();
        try {
            V1Deployment deployment = appsApi.readNamespacedDeployment(deploymentName, namespace)
                    .execute();

            int currentReplicas = deployment.getSpec().getReplicas() != null ? deployment.getSpec().getReplicas() : 0;
            deployment.getSpec().setReplicas(replicas);

            appsApi.replaceNamespacedDeployment(deploymentName, namespace, deployment)
                    .execute();

            StringBuilder sb = new StringBuilder();
            sb.append("## Deployment 스케일링 완료\n\n");
            sb.append("- **Deployment**: ").append(deploymentName).append("\n");
            sb.append("- **네임스페이스**: ").append(namespace).append("\n");
            sb.append("- **변경**: ").append(currentReplicas).append(" → ").append(replicas).append(" replicas\n");

            if (replicas > currentReplicas) {
                sb.append("\n> ℹ️ 스케일 업: 새로운 Pod가 생성됩니다. `listPods`로 상태를 확인하세요.");
            } else if (replicas < currentReplicas) {
                sb.append("\n> ⚠️ 스케일 다운: 일부 Pod가 종료됩니다.");
            }

            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Deployment 스케일링", e);
        }
    }

    public String restartDeployment(String namespace, String deploymentName) {
        ensureConnected();
        try {
            V1Deployment deployment = appsApi.readNamespacedDeployment(deploymentName, namespace)
                    .execute();

            // Trigger rolling restart by updating annotation
            Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
            if (annotations == null) {
                annotations = new HashMap<>();
                deployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
            }
            annotations.put("kubectl.kubernetes.io/restartedAt", OffsetDateTime.now().toString());

            appsApi.replaceNamespacedDeployment(deploymentName, namespace, deployment)
                    .execute();

            StringBuilder sb = new StringBuilder();
            sb.append("## Deployment 재시작 요청 완료\n\n");
            sb.append("- **Deployment**: ").append(deploymentName).append("\n");
            sb.append("- **네임스페이스**: ").append(namespace).append("\n");
            sb.append("- **방식**: 롤링 재시작 (zero-downtime)\n");
            sb.append("\n> ℹ️ 기존 Pod가 순차적으로 교체됩니다. `listPods`로 진행 상태를 확인하세요.");
            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Deployment 재시작", e);
        }
    }

    public String listServices(String namespace) {
        ensureConnected();
        try {
            V1ServiceList serviceList = coreApi.listNamespacedService(namespace)
                    .execute();

            if (serviceList.getItems().isEmpty()) {
                return "## Service 목록 — " + namespace + "\n\n> Service가 없습니다.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Service 목록 — ").append(namespace).append("\n\n");
            sb.append("| 이름 | 타입 | Cluster IP | 포트 | 생성일 |\n");
            sb.append("|------|------|-----------|------|--------|\n");

            for (V1Service svc : serviceList.getItems()) {
                String name = svc.getMetadata().getName();
                String type = svc.getSpec().getType();
                String clusterIP = svc.getSpec().getClusterIP();

                String ports = "";
                if (svc.getSpec().getPorts() != null) {
                    ports = svc.getSpec().getPorts().stream()
                            .map(p -> {
                                String portStr = p.getPort().toString();
                                if (p.getNodePort() != null) portStr += ":" + p.getNodePort();
                                portStr += "/" + p.getProtocol();
                                return portStr;
                            })
                            .collect(Collectors.joining(", "));
                }

                String age = formatAge(svc.getMetadata().getCreationTimestamp());
                sb.append(String.format("| %s | %s | %s | %s | %s |\n",
                        name, type, clusterIP, ports, age));
            }

            sb.append("\n**총 ").append(serviceList.getItems().size()).append("개 Service**");
            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("Service 목록 조회", e);
        }
    }

    public String getClusterStatus() {
        ensureConnected();
        try {
            V1NodeList nodeList = coreApi.listNode().execute();

            StringBuilder sb = new StringBuilder();
            sb.append("## 클러스터 상태\n\n");

            // Node info
            sb.append("### 노드\n");
            sb.append("| 노드 이름 | 상태 | 역할 | 버전 | OS | 아키텍처 |\n");
            sb.append("|-----------|------|------|------|-----|----------|\n");

            for (V1Node node : nodeList.getItems()) {
                String nodeName = node.getMetadata().getName();

                String status = "Unknown";
                if (node.getStatus().getConditions() != null) {
                    for (V1NodeCondition cond : node.getStatus().getConditions()) {
                        if ("Ready".equals(cond.getType())) {
                            status = "True".equals(cond.getStatus()) ? "✅ Ready" : "❌ NotReady";
                        }
                    }
                }

                String roles = "";
                if (node.getMetadata().getLabels() != null) {
                    roles = node.getMetadata().getLabels().entrySet().stream()
                            .filter(e -> e.getKey().contains("node-role.kubernetes.io/"))
                            .map(e -> e.getKey().replace("node-role.kubernetes.io/", ""))
                            .collect(Collectors.joining(", "));
                    if (roles.isEmpty()) roles = "worker";
                }

                V1NodeSystemInfo info = node.getStatus().getNodeInfo();
                sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                        nodeName, status, roles,
                        info.getKubeletVersion(),
                        info.getOsImage(),
                        info.getArchitecture()));
            }

            // Namespace summary
            V1NamespaceList nsList = coreApi.listNamespace().execute();
            sb.append("\n### 네임스페이스 요약\n");
            sb.append("| 네임스페이스 | 상태 | Pod 수 |\n");
            sb.append("|-------------|------|--------|\n");

            for (V1Namespace ns : nsList.getItems()) {
                String nsName = ns.getMetadata().getName();
                try {
                    V1PodList pods = coreApi.listNamespacedPod(nsName).execute();
                    sb.append(String.format("| %s | %s | %d |\n",
                            nsName, ns.getStatus().getPhase(), pods.getItems().size()));
                } catch (ApiException ignored) {
                    sb.append(String.format("| %s | %s | - |\n",
                            nsName, ns.getStatus().getPhase()));
                }
            }

            sb.append("\n**총 ").append(nodeList.getItems().size()).append("개 노드, ")
                    .append(nsList.getItems().size()).append("개 네임스페이스**");

            return sb.toString();

        } catch (ApiException e) {
            return handleApiException("클러스터 상태 조회", e);
        }
    }

    private String formatAge(OffsetDateTime createdAt) {
        if (createdAt == null) return "-";
        Duration duration = Duration.between(createdAt.toInstant(), OffsetDateTime.now().toInstant());
        long days = duration.toDays();
        if (days > 0) return days + "d";
        long hours = duration.toHours();
        if (hours > 0) return hours + "h";
        long minutes = duration.toMinutes();
        return minutes + "m";
    }

    private String handleApiException(String operation, ApiException e) {
        log.error("{} 실패: {} - {}", operation, e.getCode(), e.getResponseBody());
        return String.format("## ❌ %s 실패\n\n- **HTTP %d**: %s\n- **상세**: %s",
                operation, e.getCode(), e.getMessage(),
                e.getResponseBody() != null ? e.getResponseBody() : "없음");
    }
}
