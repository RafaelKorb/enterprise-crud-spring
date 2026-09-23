package com.enterprise.crud.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the hexagonal boundaries from RFC-001: dependencies point inwards and the inner layers stay free of
 * frameworks, so a leak breaks the build instead of relying on review.
 */
@AnalyzeClasses(packages = "com.enterprise.crud", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule dependenciesPointInwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("com.enterprise.crud.domain..")
            .layer("Application").definedBy("com.enterprise.crud.application..")
            .layer("Infrastructure").definedBy("com.enterprise.crud.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIsPureJava = classes()
            .that().resideInAPackage("com.enterprise.crud.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("com.enterprise.crud.domain..", "java..")
            .because("the domain must not know Spring, Jakarta, Hibernate or Lombok");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = classes()
            .that().resideInAPackage("com.enterprise.crud.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.enterprise.crud.application..", "com.enterprise.crud.domain..", "java..")
            .because("use cases are wired by infrastructure configuration, not by framework annotations");
}
