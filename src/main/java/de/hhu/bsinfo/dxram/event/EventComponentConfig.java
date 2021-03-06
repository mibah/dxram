package de.hhu.bsinfo.dxram.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import com.google.gson.annotations.Expose;

import de.hhu.bsinfo.dxram.engine.DXRAMComponentConfig;
import de.hhu.bsinfo.dxram.engine.DXRAMContext;

/**
 * Config for the EventComponent
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 24.05.2017
 */
@Data
@Accessors(prefix = "m_")
@EqualsAndHashCode(callSuper = false)
@DXRAMComponentConfig.Settings(component = EventComponent.class, supportsSuperpeer = true, supportsPeer = true)
public class EventComponentConfig extends DXRAMComponentConfig {
    private static final int THREAD_COUNT_MAX = 10;

    /**
     * Thread count for executor thread pool
     */
    @Expose
    private int m_threadCount = 1;

    @Override
    protected boolean verify(final DXRAMContext.Config p_config) {
        if (m_threadCount < 1) {
            LOGGER.error("Invalid value (%d) for m_threadCount", m_threadCount);
            return false;
        }

        if (m_threadCount > THREAD_COUNT_MAX) {
            LOGGER.error("Max limit m_threadCount: %d", THREAD_COUNT_MAX);
            return false;
        }

        return true;
    }
}
