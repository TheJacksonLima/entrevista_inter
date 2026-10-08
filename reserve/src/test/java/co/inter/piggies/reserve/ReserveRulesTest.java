package co.inter.piggies.reserve;

import org.junit.jupiter.api.Test;

import static co.inter.piggies.reserve.ReserveRules.Action.APPLY;
import static co.inter.piggies.reserve.ReserveRules.Action.CONFLICT;
import static co.inter.piggies.reserve.ReserveRules.Action.NOOP;
import static org.assertj.core.api.Assertions.assertThat;

class ReserveRulesTest {

    @Test
    void confirmTransitions() {
        assertThat(ReserveRules.confirm(ReserveStatus.PENDING)).isEqualTo(APPLY);
        assertThat(ReserveRules.confirm(ReserveStatus.CONFIRMED)).isEqualTo(NOOP);
        assertThat(ReserveRules.confirm(ReserveStatus.RELEASED)).isEqualTo(CONFLICT);
    }

    @Test
    void releaseTransitions() {
        assertThat(ReserveRules.release(ReserveStatus.PENDING)).isEqualTo(APPLY);
        assertThat(ReserveRules.release(ReserveStatus.RELEASED)).isEqualTo(NOOP);
        assertThat(ReserveRules.release(ReserveStatus.CONFIRMED)).isEqualTo(CONFLICT);
    }
}
