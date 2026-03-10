package com.devpulse.mcp.k8s.tools;

import com.devpulse.mcp.k8s.service.KubernetesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 도구 — Kubernetes 클러스터 관리
 * Pod/Deployment/Service 조회, 스케일링, 재시작, 로그 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KubernetesTools {

    private final KubernetesService kubernetesService;

    @Tool(description = "Kubernetes 네임스페이스의 Pod 목록을 조회합니다. 각 Pod의 상태, Ready 여부, 재시작 횟수, 노드, 실행 시간을 반환합니다.")
    public String listPods(
            @ToolParam(description = "Kubernetes 네임스페이스 (예: default, devpulse)") String namespace) {
        log.info("Tool: list_pods namespace={}", namespace);
        return kubernetesService.listPods(namespace);
    }

    @Tool(description = "특정 Pod의 상세 정보를 조회합니다. 기본 정보, 레이블, 컨테이너 상태, 리소스 요청/제한, 조건을 반환합니다.")
    public String getPodDetails(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace,
            @ToolParam(description = "Pod 이름") String podName) {
        log.info("Tool: get_pod_details namespace={} pod={}", namespace, podName);
        return kubernetesService.getPodDetails(namespace, podName);
    }

    @Tool(description = "Pod의 최근 로그를 조회합니다. 에러 디버깅, 애플리케이션 상태 확인에 활용합니다.")
    public String getPodLogs(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace,
            @ToolParam(description = "Pod 이름") String podName,
            @ToolParam(description = "조회할 로그 줄 수 (기본 100)") int tailLines) {
        log.info("Tool: get_pod_logs namespace={} pod={} lines={}", namespace, podName, tailLines);
        return kubernetesService.getPodLogs(namespace, podName, Math.min(tailLines, 500));
    }

    @Tool(description = "Kubernetes 네임스페이스의 Deployment 목록을 조회합니다. 각 Deployment의 레플리카 상태, 이미지, 생성일을 반환합니다.")
    public String listDeployments(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace) {
        log.info("Tool: list_deployments namespace={}", namespace);
        return kubernetesService.listDeployments(namespace);
    }

    @Tool(description = "Deployment의 레플리카 수를 변경합니다 (스케일 업/다운). 트래픽 증가 대응이나 리소스 절약에 활용합니다.")
    public String scaleDeployment(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace,
            @ToolParam(description = "Deployment 이름") String deploymentName,
            @ToolParam(description = "변경할 레플리카 수") int replicas) {
        log.info("Tool: scale_deployment namespace={} deployment={} replicas={}", namespace, deploymentName, replicas);
        return kubernetesService.scaleDeployment(namespace, deploymentName, replicas);
    }

    @Tool(description = "Deployment를 롤링 재시작합니다. 설정 변경 적용이나 문제 해결을 위해 Pod를 순차적으로 교체합니다. zero-downtime으로 진행됩니다.")
    public String restartDeployment(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace,
            @ToolParam(description = "Deployment 이름") String deploymentName) {
        log.info("Tool: restart_deployment namespace={} deployment={}", namespace, deploymentName);
        return kubernetesService.restartDeployment(namespace, deploymentName);
    }

    @Tool(description = "Kubernetes 네임스페이스의 Service 목록을 조회합니다. 각 Service의 타입, Cluster IP, 포트 정보를 반환합니다.")
    public String listServices(
            @ToolParam(description = "Kubernetes 네임스페이스") String namespace) {
        log.info("Tool: list_services namespace={}", namespace);
        return kubernetesService.listServices(namespace);
    }

    @Tool(description = "Kubernetes 클러스터의 전체 상태를 조회합니다. 노드 정보, 네임스페이스별 Pod 수, 클러스터 리소스 현황을 반환합니다.")
    public String getClusterStatus() {
        log.info("Tool: get_cluster_status");
        return kubernetesService.getClusterStatus();
    }
}
