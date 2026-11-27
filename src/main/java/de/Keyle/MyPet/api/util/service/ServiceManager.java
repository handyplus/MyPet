/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.api.util.service;

import com.google.common.collect.ArrayListMultimap;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;

import java.util.*;

/**
 * {@link ServiceManager} 负责管理与其他插件的所有交互。服务按类以及它所实现的接口存储，
 * 因此也可以通过这些接口来获取它们。你可以获取其他插件的实例，并检查其他插件是否处于活动状态。
 */
public class ServiceManager {
    Map<Class<? extends ServiceContainer>, ServiceContainer> services = new HashMap<>();
    Map<String, ServiceContainer> serviceByName = new HashMap<>();

    ArrayListMultimap<Load.State, ServiceContainer> registeredServices = ArrayListMultimap.create();

    public void listServices() {
        MyPetApi.getLogger().info("Loaded services: " + serviceByName.keySet());
    }

    /**
     * 在此注册新的服务。服务必须带有 {@link ServiceName} 注解才会被接受。
     *
     * @param serviceClass 服务类
     */
    public void registerService(Class<? extends ServiceContainer> serviceClass) {
        Load.State loadingState = Load.State.OnEnable;
        if (serviceClass.isAnnotationPresent(Load.class)) {
            loadingState = serviceClass.getAnnotation(Load.class).value();
        }
        try {
            ServiceContainer service = serviceClass.newInstance();
            registeredServices.put(loadingState, service);
        } catch (Throwable e) {
            MyPetApi.getLogger().warning("Error occured while creating the " + serviceClass.getName() + " service.");
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void registerService(ServiceContainer service) {
        boolean genericService = true;
        for (Object o : Util.getAllInterfaces(service.getClass())) {
            if (o != ServiceContainer.class && ServiceContainer.class.isAssignableFrom((Class) o)) {
                services.put((Class) o, service);
                genericService = false;
            }
        }
        for (Object o : Util.getAllSuperclasses(service.getClass())) {
            if (o != ServiceContainer.class && ServiceContainer.class.isAssignableFrom((Class) o)) {
                services.put((Class) o, service);
                genericService = false;
            }
        }
        if (genericService) {
            services.put(ServiceContainer.class, service);
        }
        serviceByName.put(service.getServiceName(), service);
        services.put(service.getClass(), service);
    }

    public void activate(Load.State state) {
        List<ServiceContainer> services = registeredServices.get(state);

        for (ServiceContainer service : services) {
            if (service.onEnable()) {
                registerService(service);
            }
        }

        registeredServices.removeAll(state);
    }

    public void disableServices() {
        for (ServiceContainer service : serviceByName.values()) {
            service.onDisable();
        }
    }

    /**
     * 返回所有继承自指定类/接口的服务
     *
     * @param serviceClass 实现自 {@link ServiceContainer} 的类
     * @return 该服务类的实例列表
     */
    @SuppressWarnings("unchecked")
    public <T extends ServiceContainer> List<T> getServices(Class<? extends T> serviceClass) {
        List<T> list = new ArrayList<>();

        for (ServiceContainer service : services.values()) {
            if (service.getClass().isAssignableFrom(serviceClass) && !list.contains(service)) {
                list.add((T) service);
            }
            if (serviceClass.isAssignableFrom(service.getClass()) && !list.contains(service)) {
                list.add((T) service);
            }
        }

        return list;
    }

    /**
     * 返回是否存在继承自指定类/接口的可用服务
     *
     * @param serviceClass 实现自 {@link ServiceContainer} 的类
     * @return 是否找到了任何服务
     */
    public boolean hasServices(Class<? extends ServiceContainer> serviceClass) {
        return services.containsKey(serviceClass);
    }

    /**
     * 返回指定类的服务
     *
     * @param serviceClass 实现自 {@link ServiceContainer} 的类
     * @return 该服务类的实例
     */
    @SuppressWarnings("unchecked")
    public <T extends ServiceContainer> Optional<T> getService(Class<? extends T> serviceClass) {
        return Optional.ofNullable((T) services.get(serviceClass));
    }

    /**
     * 返回具有指定 {@link ServiceName} 的服务
     *
     * @param name 插件名称
     * @return 与该插件名称关联的服务类实例
     */
    public Optional<ServiceContainer> getService(String name) {
        return Optional.ofNullable(serviceByName.get(name));
    }

    /**
     * 返回是否存在具有指定 {@link ServiceName} 的可用服务
     *
     * @param name 插件名称
     * @return 是否找到了任何服务
     */
    public boolean isServiceActive(String name) {
        return serviceByName.containsKey(name);
    }

    /**
     * 返回是否存在继承自指定类/接口的可用服务
     *
     * @param serviceClass 实现自 {@link ServiceContainer} 的类
     * @return 是否找到了任何服务
     */
    public boolean isServiceActive(Class<? extends ServiceContainer> serviceClass) {
        return services.containsKey(serviceClass);
    }
}