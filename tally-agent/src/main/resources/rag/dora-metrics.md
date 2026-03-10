# DORA Metrics — DevOps Research and Assessment

DORA metrics are the gold standard for measuring software delivery performance and operational excellence, developed by the DORA team at Google Cloud.

## Four Key Metrics

### 1. Deployment Frequency
How often code is deployed to production.
- **Elite**: On-demand (multiple deploys per day)
- **High**: Between once per day and once per week
- **Medium**: Between once per week and once per month
- **Low**: Between once per month and once every six months

### 2. Lead Time for Changes
Time from code commit to running in production.
- **Elite**: Less than one hour
- **High**: Between one day and one week
- **Medium**: Between one week and one month
- **Low**: Between one month and six months

### 3. Change Failure Rate
Percentage of deployments causing a failure in production.
- **Elite**: 0-5%
- **High**: 5-10%
- **Medium**: 10-15%
- **Low**: 46-60%

### 4. Mean Time to Recovery (MTTR)
How long it takes to recover from a failure in production.
- **Elite**: Less than one hour
- **High**: Less than one day
- **Medium**: Between one day and one week
- **Low**: More than six months

## Proxy Metrics from GitHub Data
When direct DORA metrics are unavailable, these GitHub proxies can estimate performance:
- **Deployment Frequency** → Merge frequency to main branch, release frequency
- **Lead Time** → PR open-to-merge time, first commit to merge time
- **Change Failure Rate** → Revert commit ratio, hotfix branch frequency
- **MTTR** → Time between bug report and fix merge, hotfix PR merge time

## Team Health Indicators
- **Bus Factor**: Number of contributors who must be hit by a bus before a project is stuck. Bus Factor of 1 is critical risk.
- **PR Review Time**: Average time for PR review. Target < 24 hours.
- **PR Merge Rate**: Percentage of PRs merged vs closed without merge. Target > 80%.
- **Code Review Coverage**: Percentage of PRs with at least one review. Target 100%.
- **Contributor Balance**: Gini coefficient of commits across team members. Lower is better (more balanced).
