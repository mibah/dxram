package de.hhu.bsinfo.dxram.engine;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.google.gson.annotations.Expose;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides configuration values for a service. Use this as a base class for all services to add further configuration values
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 24.05.2017
 */
@Accessors(prefix = "m_")
public class DXRAMServiceConfig {
    protected final Logger LOGGER;

    /**
     * Fully qualified class name of config to allow object creation with gson
     */
    @Expose
    private String m_classConfig;

    /**
     * Get the class name of the service of this configuration
     */
    @Getter
    private String m_serviceClassName;

    /**
     * True if service supports the superpeer node role, false otherwise
     */
    @Getter
    private boolean m_supportsSuperpeer = false;

    /**
     * True if service supports the peer node role, false otherwise
     */
    @Getter
    private boolean m_supportsPeer = false;

    /**
     * Constructor
     */
    public DXRAMServiceConfig() {
        LOGGER = LogManager.getFormatterLogger(getClass().getSimpleName());
        m_classConfig = getClass().getName();

        Annotation[] annotations = getClass().getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Settings) {
                Settings ann = (Settings) annotation;
                m_serviceClassName = ann.service().getSimpleName();
                m_supportsSuperpeer = ann.supportsSuperpeer();
                m_supportsPeer = ann.supportsPeer();
            }
        }
    }

    /**
     * Verify the configuration values: Check limits, validate strings, ...
     *
     * @param p_config
     *         Full configuration to access other config values on dependencies
     * @return True if verifcation successful, false on error
     */
    protected boolean verify(final DXRAMContext.Config p_config) {
        return true;
    }

    /**
     * Settings for service config
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    public @interface Settings {
        /**
         * The service class for this configuration
         */
        Class<? extends AbstractDXRAMService> service();

        /**
         * True if service supports the superpeer node role, false otherwise
         */
        boolean supportsSuperpeer();

        /**
         * True if service supports the peer node role, false otherwise
         */
        boolean supportsPeer();
    }
}
