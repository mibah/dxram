/*
 * Copyright (C) 2018 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.engine;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.dxutils.unit.IPV4Unit;

/**
 * Context object with settings for the engine as well as component and service instances
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 20.10.2016
 */
@ToString
public class DXRAMContext {
    private static final Logger LOGGER = LogManager.getFormatterLogger(DXRAMContext.class.getSimpleName());

    /**
     * Engine specific settings
     */
    @Expose
    private Config m_config = new Config();

    /**
     * List of components
     */
    private final Map<String, AbstractDXRAMComponent> m_components = new HashMap<>();

    /**
     * List of services
     */
    private final Map<String, AbstractDXRAMService> m_services = new HashMap<>();

    /**
     * Constructor
     */
    public DXRAMContext() {

    }

    /**
     * Get the configuration
     *
     * @return Configuration
     */
    public Config getConfig() {
        return m_config;
    }

    /**
     * Get all component instances
     *
     * @return Component instances
     */
    public Map<String, AbstractDXRAMComponent> getComponents() {
        return m_components;
    }

    /**
     * Get all service instances
     *
     * @return Service instances
     */
    public Map<String, AbstractDXRAMService> getServices() {
        return m_services;
    }

    /**
     * Run configuration value verification on all component configurations
     *
     * @return True if verification successful, false on failure
     */
    boolean verifyConfigurationValuesComponents() {
        for (DXRAMComponentConfig config : m_config.m_componentConfigs.values()) {
            LOGGER.debug("Verifying component configuration values of %s...", config.getComponentClassName());

            if (!config.verify(m_config)) {
                LOGGER.error("Verifying component configuration values failed (%s)", config.getComponentClassName());

                return false;
            }
        }

        return true;
    }

    /**
     * Run configuration value verification on all service configurations
     *
     * @return True if verification successful, false on failure
     */
    boolean verifyConfigurationValuesServices() {
        for (DXRAMServiceConfig config : m_config.m_serviceConfigs.values()) {
            LOGGER.debug("Verifying service configuration values of %s...", config.getServiceClassName());

            if (!config.verify(m_config)) {
                LOGGER.error("Verifying service configuration values failed (%s)", config.getServiceClassName());

                return false;
            }
        }

        return true;
    }

    /**
     * Create component instances based on the current configuration
     *
     * @param p_manager
     *         Manager to use
     * @param p_nodeRole
     *         Current node role
     */
    void createComponentsFromConfig(final DXRAMComponentManager p_manager, final NodeRole p_nodeRole) {
        m_components.clear();

        for (DXRAMComponentConfig config : m_config.m_componentConfigs.values()) {
            if (p_nodeRole == NodeRole.SUPERPEER && config.isSupportsSuperpeer() ||
                    p_nodeRole == NodeRole.PEER && config.isSupportsPeer()) {
                AbstractDXRAMComponent comp = p_manager.createInstance(config.getComponentClassName());
                comp.setConfig(config);

                if (p_nodeRole == NodeRole.SUPERPEER && comp.supportsSuperpeer() ||
                        p_nodeRole == NodeRole.PEER && comp.supportsPeer()) {
                    m_components.put(comp.getClass().getSimpleName(), comp);
                } else {
                    LOGGER.error(
                            "Creating instance of component '%s' not possible on current node type '%s', not supported",
                            comp.getComponentName(),
                            p_nodeRole);
                }
            }
        }
    }

    /**
     * Create service instances based on the current configuration
     *
     * @param p_manager
     *         Manager to use
     * @param p_nodeRole
     *         Current node role
     */
    void createServicesFromConfig(final DXRAMServiceManager p_manager, final NodeRole p_nodeRole) {
        m_services.clear();

        for (DXRAMServiceConfig config : m_config.m_serviceConfigs.values()) {
            if (p_nodeRole == NodeRole.SUPERPEER && config.isSupportsSuperpeer() ||
                    p_nodeRole == NodeRole.PEER && config.isSupportsPeer()) {
                AbstractDXRAMService serv = p_manager.createInstance(config.getServiceClassName());
                serv.setConfig(config);

                if (p_nodeRole == NodeRole.SUPERPEER && serv.supportsSuperpeer() ||
                        p_nodeRole == NodeRole.PEER && serv.supportsPeer()) {
                    m_services.put(serv.getClass().getSimpleName(), serv);
                } else {
                    LOGGER.error(
                            "Creating instance of service '%s' not possible on current node type '%s', not supported",
                            serv.getServiceName(), p_nodeRole);
                }
            }
        }
    }

    /**
     * Fill the context with all components that registered at the DXRAMComponentManager
     *
     * @param p_manager
     *         Manager to use
     */
    public void createDefaultComponents(final DXRAMComponentManager p_manager) {
        m_components.clear();
        m_config.m_componentConfigs.clear();

        for (AbstractDXRAMComponent component : p_manager.createAllInstances()) {
            m_components.put(component.getClass().getSimpleName(), component);
            m_config.m_componentConfigs.put(component.getConfig().getClass().getSimpleName(), component.getConfig());
        }
    }

    /**
     * Fill the context with all services that registered at the DXRAMServiceManager
     *
     * @param p_manager
     *         Manager to use
     */
    public void createDefaultServices(final DXRAMServiceManager p_manager) {
        m_services.clear();
        m_config.m_serviceConfigs.clear();

        for (AbstractDXRAMService service : p_manager.createAllInstances()) {
            m_services.put(service.getClass().getSimpleName(), service);
            m_config.m_serviceConfigs.put(service.getConfig().getClass().getSimpleName(), service.getConfig());
        }
    }

    /**
     * Class providing configuration values for engine and all components/services
     */
    @ToString
    public static class Config {
        /**
         * Engine specific settings
         */
        @Expose
        private EngineConfig m_engineConfig = new EngineConfig();

        /**
         * Component configurations
         */
        @Expose
        private Map<String, DXRAMComponentConfig> m_componentConfigs = new HashMap<>();

        /**
         * Service configurations
         */
        @Expose
        private Map<String, DXRAMServiceConfig> m_serviceConfigs = new HashMap<>();

        /**
         * Get the engine configuration
         */
        public EngineConfig getEngineConfig() {
            return m_engineConfig;
        }

        /**
         * Get the configuration of a specific component
         *
         * @param p_class
         *         Class of the component configuration to get
         * @return Component configuration class
         */
        public <T extends DXRAMComponentConfig> T getComponentConfig(final Class<T> p_class) {
            DXRAMComponentConfig conf = m_componentConfigs.get(p_class.getSimpleName());

            return p_class.cast(conf);
        }

        /**
         * Get the configuration of a specific service
         *
         * @param p_class
         *         Class of the service configuration to get
         * @return Service configuration class
         */
        public <T extends DXRAMServiceConfig> T getServiceConfig(final Class<T> p_class) {
            DXRAMServiceConfig conf = m_serviceConfigs.get(p_class.getSimpleName());

            return p_class.cast(conf);
        }
    }

    /**
     * Config for the engine
     */
    @Data
    @Accessors(prefix = "m_")
    @ToString
    public static class EngineConfig {
        /**
         * Address and port of this instance
         */
        @Expose
        private IPV4Unit m_address = new IPV4Unit("127.0.0.1", 22222);

        /**
         * Role of this instance (superpeer, peer, terminal)
         */
        @Expose
        private String m_role = "Peer";

        /**
         * Path to jni dependencies
         */
        @Expose
        private String m_jniPath = "jni";

        /**
         * Role assigned for this DXRAM instance
         *
         * @return Role
         */
        public NodeRole getRole() {
            return NodeRole.toNodeRole(m_role);
        }
    }
}
