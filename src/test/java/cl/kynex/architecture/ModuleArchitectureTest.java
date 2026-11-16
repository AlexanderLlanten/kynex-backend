package cl.kynex.architecture;

import cl.kynex.catalog.service.InventoryAccessService;
import cl.kynex.identity.service.AccountAccessService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba el código compilado para impedir dependencias que rompan los límites de los módulos. */
class ModuleArchitectureTest {
    private static final JavaClasses APPLICATION = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("cl.kynex");
    private static final List<String> MODULES = List.of("identity", "catalog", "commerce", "training", "contact");

    @Test void productionClassesFollowTheDeclaredModuleGraph() {
        classes().should().resideInAnyPackage("cl.kynex", "cl.kynex.config..", "cl.kynex.shared..",
            "cl.kynex.identity..", "cl.kynex.catalog..", "cl.kynex.commerce..", "cl.kynex.training..", "cl.kynex.contact..")
            .check(APPLICATION);
        for (var module : MODULES) {
            String[] allowed = switch (module) {
                case "commerce" -> new String[]{"cl.kynex.commerce..", "cl.kynex.identity.api..",
                    "cl.kynex.catalog.api..", "cl.kynex.catalog.model..", "cl.kynex.shared.."};
                case "contact", "training" -> new String[]{"cl.kynex." + module + "..", "cl.kynex.identity.api..", "cl.kynex.shared.."};
                default -> new String[]{"cl.kynex." + module + "..", "cl.kynex.shared.."};
            };
            classes().that().resideInAPackage("cl.kynex." + module + "..")
                .should().onlyDependOnClassesThat(resideOutsideOfPackage("cl.kynex..").or(resideInAnyPackage(allowed)))
                .check(APPLICATION);
        }
    }

    @Test void modulesHaveNoDependencyCycles() {
        slices().matching("cl.kynex.(*)..").should().beFreeOfCycles().check(APPLICATION);
    }

    @Test void repositoriesAreUsedOnlyInsideTheirOwnModule() {
        for (var module : MODULES)
            classes().that().resideInAPackage("cl.kynex." + module + ".repository..")
                .should().onlyHaveDependentClassesThat().resideInAPackage("cl.kynex." + module + "..")
                .check(APPLICATION);
    }

    @Test void sharedCodeDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAnyPackage("cl.kynex.shared..", "cl.kynex.config..")
            .should().dependOnClassesThat().resideInAnyPackage("cl.kynex.identity..", "cl.kynex.catalog..",
                "cl.kynex.commerce..", "cl.kynex.training..", "cl.kynex.contact..").check(APPLICATION);
    }

    @Test void consumersAccessIdentityThroughItsInternalApi() {
        noClasses().that().resideOutsideOfPackage("cl.kynex.identity..")
            .should().dependOnClassesThat().resideInAnyPackage("cl.kynex.identity.model..", "cl.kynex.identity.repository..",
                "cl.kynex.identity.service..", "cl.kynex.identity.controller..", "cl.kynex.identity.dto..", "cl.kynex.identity.config..")
            .check(APPLICATION);
    }

    @Test void catalogExposesOnlyInventoryApiAndTransactionalModels() {
        // Comercio conserva la relación JPA a Product para que pago e inventario sigan siendo atómicos.
        noClasses().that().resideOutsideOfPackage("cl.kynex.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("cl.kynex.catalog.repository..", "cl.kynex.catalog.service..",
                "cl.kynex.catalog.controller..", "cl.kynex.catalog.dto..").check(APPLICATION);
    }

    @Test void contactAndTrainingDoNotDependOnCommerceOrEachOther() {
        noClasses().that().resideInAPackage("cl.kynex.contact..")
            .should().dependOnClassesThat().resideInAnyPackage("cl.kynex.commerce..", "cl.kynex.training..", "cl.kynex.catalog..")
            .check(APPLICATION);
        noClasses().that().resideInAPackage("cl.kynex.training..")
            .should().dependOnClassesThat().resideInAnyPackage("cl.kynex.commerce..", "cl.kynex.contact..", "cl.kynex.catalog..")
            .check(APPLICATION);
    }

    @Test void controllersDelegatePersistenceToServices() {
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..").check(APPLICATION);
    }

    @Test void modelsDoNotDependOnServicesRepositoriesOrControllers() {
        noClasses().that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..")
            .check(APPLICATION);
    }

    @Test void internalAccessKeepsTheCallingTransactionAndItsLocks() {
        for (var implementation : List.of(AccountAccessService.class, InventoryAccessService.class))
            assertThat(implementation.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
