# Conventional Commits 1.0.0

Conventional Commits is a specification for adding human and machine-readable meaning to commit messages.

## Format
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Types
- **feat**: A new feature (correlates with MINOR in SemVer)
- **fix**: A bug fix (correlates with PATCH in SemVer)
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code (white-space, formatting)
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **build**: Changes that affect the build system or external dependencies
- **ci**: Changes to CI configuration files and scripts
- **chore**: Other changes that don't modify src or test files
- **revert**: Reverts a previous commit

## Breaking Changes
- Append `!` after the type/scope: `feat!: breaking change`
- Or add `BREAKING CHANGE:` in footer

## Quality Criteria
- **Grade A (Excellent)**: 80%+ conventional commits, average commit size < 200 lines
- **Grade B (Good)**: 60%+ conventional commits, average commit size < 300 lines
- **Grade C (Average)**: 40%+ conventional commits, average commit size < 500 lines
- **Grade D (Below Average)**: 20%+ conventional commits
- **Grade F (Poor)**: Less than 20% conventional commits

## Best Practices
- Keep commits atomic: one logical change per commit
- Write descriptive commit messages explaining "why" not just "what"
- Use scopes to indicate the area of change: `feat(auth): add OAuth2 login`
- Commit message body should explain motivation for the change
- Reference issue numbers in footer: `Closes #123`
