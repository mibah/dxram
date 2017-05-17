/*
 * Copyright (C) 2017 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science, Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.engine;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;

import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.utils.unit.IPV4Unit;

/**
 * Context object with settings for the engine as well as component and service instances
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 20.10.2016
 */
public class DXRAMContext {

    /**
     * Engine specific settings
     */
    @Expose
    private EngineSettings m_engineSettings = new EngineSettings();
    /**
     * List of components
     */
    @Expose
    private Map<String, AbstractDXRAMComponent> m_components = new HashMap<>();
    /**
     * List of services
     */
    @Expose
    private Map<String, AbstractDXRAMService> m_services = new HashMap<>();

    /**
     * Constructor
     */
    public DXRAMContext() {

    }

    /**
     * Get the engine settings
     *
     * @return Engine settings
     */
    EngineSettings getEngineSettings() {
        return m_engineSettings;
    }

    /**
     * Get all component instances
     *
     * @return Component instances
     */
    Map<String, AbstractDXRAMComponent> getComponents() {
        return m_components;
    }

    /**
     * Get all service instances
     *
     * @return Service instances
     */
    Map<String, AbstractDXRAMService> getServices() {
        return m_services;
    }

    /**
     * Fill the context with all components that registered at the DXRAMComponentManager
     *
     * @param p_manager
     *     Manager to use
     */
    void fillDefaultComponents(final DXRAMComponentManager p_manager) {

        for (AbstractDXRAMComponent component : p_manager.createAllInstances()) {
            m_components.put(component.getClass().getSimpleName(), component);
        }
    }

    /**
     * Fill the context with all services that registered at the DXRAMServiceManager
     *
     * @param p_manager
     *     Manager to use
     */
    void fillDefaultServices(final DXRAMServiceManager p_manager) {

        for (AbstractDXRAMService service : p_manager.createAllInstances()) {
            m_services.put(service.getClass().getSimpleName(), service);
        }
    }

    /**
     * Settings for the engine
     */
    public static class EngineSettings {

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
         * Path to application modules (jar files)
         */
        @Expose
        private String m_applicationModulePath = "app";

        /**
         * Constructor
         */
        EngineSettings() {

        }

        /**
         * Get the address assigned to the DXRAM instance
         *
         * @return Address
         */
        public IPV4Unit getAddress() {
            return m_address;
        }

        /**
         * Role assigned for this DXRAM instance
         *
         * @return Role
         */
        public NodeRole getRole() {
            return NodeRole.toNodeRole(m_role);
        }

        /**
         * Get the path to the folder with the JNI compiled libraries
         *
         * @return Path to JNI libraries
         */
        String getJNIPath() {
            return m_jniPath;
        }

        /**
         * Get the path to the folder with the compiled application modules (jar files)
         *
         * @return Path to JNI libraries
         */
        String getApplicationModulePath() {
            return m_applicationModulePath;
        }
    }
}
