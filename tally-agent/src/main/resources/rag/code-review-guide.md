# Code Review Best Practices (Based on Google Engineering Practices)

## Purpose of Code Review
- Maintain code quality and consistency
- Share knowledge across the team
- Catch bugs before they reach production
- Ensure code is maintainable and readable

## For Reviewers

### What to Look For
1. **Design**: Is the code well-designed and appropriate for the system?
2. **Functionality**: Does the code behave as intended? Is it good for users?
3. **Complexity**: Could the code be simpler? Would another developer understand it?
4. **Tests**: Does the code have correct, well-designed, useful automated tests?
5. **Naming**: Are names clear and descriptive?
6. **Comments**: Are comments clear and useful? Do they explain "why" not "what"?
7. **Style**: Does the code follow the team's style guide?
8. **Documentation**: Did the developer update relevant documentation?

### Review Speed
- Aim to review within **one business day** (ideally same day)
- Don't let perfectionism block progress
- If you're in the middle of focused work, review at a natural break point

### Review Tone
- Be kind and constructive
- Comment on the code, not the developer
- Explain "why" behind suggestions
- Distinguish between required changes and optional suggestions (use "Nit:" prefix)

## For Authors

### Writing Good PRs
- **Keep PRs small**: 200-400 lines changed is ideal. Large PRs (>1000 lines) are hard to review effectively.
- **Self-review first**: Review your own PR before requesting review
- **Descriptive title and description**: Explain what and why
- **One concern per PR**: Don't mix refactoring with features
- **Include tests**: Every PR should include relevant tests

### PR Quality Grades
- **Grade A**: Merge rate ≥ 80%, average review time < 24 hours
- **Grade B**: Merge rate ≥ 70%, average review time < 48 hours
- **Grade C**: Merge rate ≥ 60%, average review time < 72 hours
- **Grade D**: Merge rate ≥ 50%
- **Grade F**: Merge rate < 50%

## PR Size Guidelines
- **Small (< 100 lines)**: Quick to review, easy to understand
- **Medium (100-400 lines)**: Standard, reasonable review time
- **Large (400-1000 lines)**: Consider splitting if possible
- **Very Large (> 1000 lines)**: Should almost always be split into smaller PRs
