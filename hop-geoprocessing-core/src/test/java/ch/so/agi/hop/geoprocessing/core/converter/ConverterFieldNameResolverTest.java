package ch.so.agi.hop.geoprocessing.core.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class ConverterFieldNameResolverTest {
  @Test
  void resolvesRequiredAndOptionalValuesFromHopVariables() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("SOURCE", "geometry_in");
    assertThat(ConverterFieldNameResolver.resolveRequired("${SOURCE}", "input_field", variables))
        .isEqualTo("geometry_in");
    assertThat(ConverterFieldNameResolver.resolveOptional("${SOURCE}", "output_field", variables))
        .isEqualTo("geometry_in");
  }

  @Test
  void optionalBlankIsInactiveButOptionalEmptyResolutionIsAnError() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("EMPTY", "");
    assertThat(ConverterFieldNameResolver.resolveOptional("  ", "output_field", variables))
        .isNull();
    assertThatThrownBy(
            () -> ConverterFieldNameResolver.resolveOptional("${EMPTY}", "output_field", variables))
        .isInstanceOf(ConverterConfigurationException.class)
        .satisfies(
            error ->
                assertThat(issues(error))
                    .extracting(ConfigurationIssue::code)
                    .containsExactly(ConfigurationIssue.GC_CONFIG_INVALID));
  }

  @Test
  void unresolvedAndMissingRequiredValuesAreNamedErrors() {
    Variables variables = new Variables();
    assertThatThrownBy(
            () -> ConverterFieldNameResolver.resolveRequired("${MISSING}", "input_field", variables))
        .isInstanceOf(ConverterConfigurationException.class)
        .satisfies(
            error ->
                assertThat(issues(error))
                    .extracting(ConfigurationIssue::code)
                    .containsExactly(ConfigurationIssue.GC_UNRESOLVED_VARIABLE));
    assertThatThrownBy(
            () -> ConverterFieldNameResolver.resolveRequired("   ", "input_field", variables))
        .isInstanceOf(ConverterConfigurationException.class)
        .satisfies(
            error ->
                assertThat(issues(error))
                    .extracting(ConfigurationIssue::code)
                    .containsExactly(ConfigurationIssue.GC_CONFIG_INVALID));
  }

  @Test
  void rawStringWithoutVariablesIsPreserved() throws Exception {
    Variables variables = new Variables();
    assertThat(ConverterFieldNameResolver.resolveRequired("literal_field", "input_field", variables))
        .isEqualTo("literal_field");
  }

  private static java.util.List<ConfigurationIssue> issues(Throwable error) {
    return ((ConverterConfigurationException) error).issues();
  }
}
