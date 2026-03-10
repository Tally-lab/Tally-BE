# OWASP Top 10 (2021) — Web Application Security Risks

## A01: Broken Access Control
- Most common vulnerability (94% of apps tested)
- Enforce access control at the server side
- Deny by default except for public resources
- Implement proper RBAC and attribute-based access control
- Log access control failures and alert administrators

## A02: Cryptographic Failures
- Previously "Sensitive Data Exposure"
- Classify data processed, stored, or transmitted
- Don't store sensitive data unnecessarily
- Encrypt all sensitive data at rest and in transit
- Use strong, up-to-date algorithms and protocols (AES-256, RSA-2048+)
- Never use deprecated hash functions (MD5, SHA1 for security)

## A03: Injection
- Includes SQL injection, NoSQL injection, OS command injection, XSS
- Use parameterized queries and prepared statements
- Validate and sanitize all user input
- Use LIMIT and other SQL controls to prevent mass disclosure
- Use safe APIs that avoid the use of the interpreter entirely

## A04: Insecure Design
- Shift-left security: integrate threat modeling early
- Use secure design patterns and reference architectures
- Write unit and integration tests for security flows
- Separate tier layers based on exposure and protection needs

## A05: Security Misconfiguration
- Remove unnecessary features, components, documentation
- Review and update configurations as part of patch management
- Use different credentials for different environments
- Automated verification of configuration effectiveness

## A06: Vulnerable and Outdated Components
- Remove unused dependencies and unnecessary features
- Continuously inventory component versions (both client and server)
- Monitor CVE databases for vulnerabilities
- Only obtain components from official sources over secure links
- Automate dependency updates with tools like Dependabot or Renovate

## A07: Identification and Authentication Failures
- Implement multi-factor authentication where possible
- Don't ship with default credentials
- Implement weak-password checks
- Limit or increasingly delay failed login attempts
- Use secure session management (server-side, random session ID)

## A08: Software and Data Integrity Failures
- Verify software and data integrity using digital signatures
- Ensure CI/CD pipeline has proper access control and integrity verification
- Don't send unsigned or unencrypted serialized data to untrusted clients
- Review code and configuration changes through proper process

## A09: Security Logging and Monitoring Failures
- Log all login attempts, access control failures, server-side validation failures
- Ensure logs are in a format that can be consumed by log management solutions
- Establish effective monitoring and alerting
- Ensure high-value transactions have an audit trail

## A10: Server-Side Request Forgery (SSRF)
- Sanitize and validate all client-supplied input data
- Enforce URL schema, port, and destination with a positive allow list
- Don't send raw responses to clients
- Disable HTTP redirections for server-side requests

## Code Review Security Checklist
When reviewing code, check for:
- [ ] Input validation on all user inputs
- [ ] Parameterized queries (no string concatenation in SQL)
- [ ] Proper authentication and authorization checks
- [ ] No hardcoded secrets or credentials
- [ ] Secure session management
- [ ] HTTPS enforcement
- [ ] Proper error handling (no sensitive info in error messages)
- [ ] Dependency versions up to date
- [ ] Logging of security-relevant events
