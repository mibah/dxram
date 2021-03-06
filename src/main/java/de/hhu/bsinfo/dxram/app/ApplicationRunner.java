package de.hhu.bsinfo.dxram.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.dxram.engine.DXRAMEngine;
import de.hhu.bsinfo.dxram.engine.DXRAMVersion;

/**
 * Runner to run applications and keep track of already running ones
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 05.09.18
 */
public class ApplicationRunner implements ApplicationCallbackHandler {
    private static final Logger LOGGER = LogManager.getFormatterLogger(ApplicationRunner.class.getSimpleName());

    private final ApplicationLoader m_loader;
    private final DXRAMVersion m_dxramVersion;
    private final DXRAMEngine m_dxramEngine;

    private HashMap<String, AbstractApplication> m_runningApplications;

    /**
     * Constructor
     *
     * @param p_loader
     *         Instance of loader to use
     * @param p_dxramVersion
     *         Version of DXRAM running on
     * @param p_dxramEngine
     *         Parent DXRAM engine
     */
    ApplicationRunner(final ApplicationLoader p_loader, final DXRAMVersion p_dxramVersion,
            final DXRAMEngine p_dxramEngine) {
        m_loader = p_loader;
        m_dxramVersion = p_dxramVersion;
        m_dxramEngine = p_dxramEngine;

        m_runningApplications = new HashMap<>();
    }

    /**
     * Start an application
     *
     * @param p_class
     *         Fully qualified class name of application to start
     * @param p_args
     *         Arguments for application
     * @return True if starting application was successful, false on error
     */
    public boolean startApplication(final String p_class, final String[] p_args) {
        if (p_class.isEmpty()) {
            return false;
        }

        if (m_runningApplications.get(p_class) != null) {
            LOGGER.error("Cannot start application '%s', an instance is already running", p_class);
            return false;
        }

        Class<? extends AbstractApplication> appClass = m_loader.getApplicationClass(p_class);

        if (appClass == null) {
            LOGGER.warn("Application class %s was not found", p_class);
            return false;
        }

        AbstractApplication app;

        try {
            app = appClass.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            LOGGER.error("Creating instance of application class %s failed: %s", appClass.getName(),
                    e.getMessage());
            return false;
        }

        // verify if built against current version
        if (!app.getBuiltAgainstVersion().compareCompatibility(m_dxramVersion)) {
            LOGGER.error("Cannot start application '%s' with version %s, not compatible with current DXRAM " +
                    "version %s", app.getName(), app.getBuiltAgainstVersion(), m_dxramVersion);

            return false;
        }

        app.init(m_dxramEngine, this, p_args);
        app.start();

        m_runningApplications.put(p_class, app);

        return true;
    }

    /**
     * Shutdown a running application. This triggers the shutdown signal to allow the application
     * to initiate a soft shutdown
     *
     * @param p_class
     *         Fully qualified class name of application to shut down
     */
    public boolean shutdownApplication(final String p_class) {
        AbstractApplication app = m_runningApplications.get(p_class);

        if (app == null) {
            LOGGER.warn("Shutting down application '%s' failed, no running instance found", p_class);
            return false;
        }

        LOGGER.debug("Signaling shutdown to application %s", p_class);

        app.signalShutdown();

        return true;
    }

    /**
     * Get a list of currently running applications
     *
     * @return List of currently running applications
     */
    public List<String> getApplicationsRunning() {
        return new ArrayList<>(m_runningApplications.keySet());
    }

    /**
     * Shut down the runner which signals a shutdown to still running applications and waiting for
     * their termination
     */
    public void shutdown() {
        LOGGER.info("Shutting down runner, signaling shut down to all running applications first");

        for (Map.Entry<String, AbstractApplication> entry : m_runningApplications.entrySet()) {
            entry.getValue().signalShutdown();
        }

        LOGGER.info("Waiting for applications to finish");

        for (Map.Entry<String, AbstractApplication> entry : m_runningApplications.entrySet()) {
            LOGGER.debug("Waiting for %s...", entry.getKey());

            try {
                entry.getValue().join();
            } catch (InterruptedException ignored) {

            }
        }
    }

    @Override
    public void started(final AbstractApplication p_application) {
        LOGGER.debug("Application started: %s", p_application);
    }

    @Override
    public void finished(final AbstractApplication p_application) {
        LOGGER.debug("Application finished: %s", p_application);

        m_runningApplications.remove(p_application.getClass().getName());
    }
}
