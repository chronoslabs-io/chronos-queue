ruleset {
    description 'Groovy RuleSet'

    //basic
    DeadCode
    EmptyCatchBlock
    EqualsAndHashCode
    ReturnFromFinallyBlock

    //imports
    NoWildcardImports
    UnnecessaryGroovyImport
    UnusedImport

    //convention
    FieldTypeRequired
    MethodParameterTypeRequired

    //formatting
    ConsecutiveBlankLines
    BlockEndsWithBlankLine
    ClassEndsWithBlankLine {
        blankLineRequired = false
    }

    //unnecessary
    UnnecessarySemicolon
    UnnecessaryPublicModifier

    ruleset('rulesets/unused.xml')
}
