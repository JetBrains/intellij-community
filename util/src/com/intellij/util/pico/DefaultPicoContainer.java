/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package com.intellij.util.pico;

import com.intellij.util.containers.OrderedSet;
import org.picocontainer.*;
import org.picocontainer.defaults.*;
import org.picocontainer.monitors.DefaultComponentMonitor;

import java.io.Serializable;
import java.util.*;

/**
 * <p/>
 * The Standard {@link org.picocontainer.PicoContainer}/{@link org.picocontainer.MutablePicoContainer} implementation.
 * Constructing a container c with a parent p container will cause c to look up components
 * in p if they cannot be found inside c itself.
 * </p>
 * <p/>
 * Using {@link Class} objects as keys to the various registerXXX() methods makes
 * a subtle semantic difference:
 * </p>
 * <p/>
 * If there are more than one registered components of the same type and one of them are
 * registered with a {@link Class} key of the corresponding type, this component
 * will take precedence over other components during type resolution.
 * </p>
 * <p/>
 * Another place where keys that are classes make a subtle difference is in
 * {@link org.picocontainer.defaults.ImplementationHidingComponentAdapter}.
 * </p>
 * <p/>
 * This implementation of {@link org.picocontainer.MutablePicoContainer} also supports
 * {@link org.picocontainer.defaults.ComponentMonitorStrategy}.
 * </p>
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 * @author Thomas Heller
 * @author Mauro Talevi
 * @version $Revision: 1.8 $
 */
public class DefaultPicoContainer implements MutablePicoContainer, ComponentMonitorStrategy, Serializable {
    private Map componentKeyToAdapterCache = new HashMap();
    private ComponentAdapterFactory componentAdapterFactory;
    private PicoContainer parent;
    private Set children = new HashSet();

    private Collection componentAdapters = new OrderedSet();
    // Keeps track of instantiation order.
    private List orderedComponentAdapters = new ArrayList();

    // Keeps track of the container started status
    private boolean started = false;
    // Keeps track of the container disposed status
    private boolean disposed = false;
    // Keeps track of child containers started status
    private Set childrenStarted = new HashSet();

    private LifecycleManager lifecycleManager = new OrderedComponentAdapterLifecycleManager();
    private LifecycleStrategy lifecycleStrategyForInstanceRegistrations;

    /**
     * Creates a new container with a custom ComponentAdapterFactory and a parent container.
     * <p/>
     * <em>
     * Important note about caching: If you intend the components to be cached, you should pass
     * in a factory that creates {@link org.picocontainer.defaults.CachingComponentAdapter} instances, such as for example
     * {@link org.picocontainer.defaults.CachingComponentAdapterFactory}. CachingComponentAdapterFactory can delegate to
     * other ComponentAdapterFactories.
     * </em>
     *
     * @param componentAdapterFactory the factory to use for creation of ComponentAdapters.
     * @param parent                  the parent container (used for component dependency lookups).
     */
    public DefaultPicoContainer(ComponentAdapterFactory componentAdapterFactory, PicoContainer parent) {
        this(componentAdapterFactory, new DefaultLifecycleStrategy(new DefaultComponentMonitor()), parent);
    }

    /**
     * Creates a new container with a custom ComponentAdapterFactory, LifecycleStrategy for instance registration,
     *  and a parent container.
     * <p/>
     * <em>
     * Important note about caching: If you intend the components to be cached, you should pass
     * in a factory that creates {@link org.picocontainer.defaults.CachingComponentAdapter} instances, such as for example
     * {@link org.picocontainer.defaults.CachingComponentAdapterFactory}. CachingComponentAdapterFactory can delegate to
     * other ComponentAdapterFactories.
     * </em>
     *
     * @param componentAdapterFactory the factory to use for creation of ComponentAdapters.
     * @param lifecycleStrategyForInstanceRegistrations the lifecylce strategy chosen for regiered
     *          instance (not implementations!)
     * @param parent                  the parent container (used for component dependency lookups).
     */
    public DefaultPicoContainer(ComponentAdapterFactory componentAdapterFactory,
                                LifecycleStrategy lifecycleStrategyForInstanceRegistrations,
                                PicoContainer parent) {
        if (componentAdapterFactory == null) throw new NullPointerException("componentAdapterFactory");
        if (lifecycleStrategyForInstanceRegistrations == null) throw new NullPointerException("lifecycleStrategyForInstanceRegistrations");
        this.componentAdapterFactory = componentAdapterFactory;
        this.lifecycleStrategyForInstanceRegistrations = lifecycleStrategyForInstanceRegistrations;
        this.parent = parent == null ? null : ImmutablePicoContainerProxyFactory.newProxyInstance(parent);
    }

    /**
      * Creates a new container with the DefaultComponentAdapterFactory using a
      * custom ComponentMonitor
      *
      * @param monitor the ComponentMonitor to use
      * @param parent the parent container (used for component dependency lookups).
      */
    public DefaultPicoContainer(ComponentMonitor monitor, PicoContainer parent) {
        this(new DefaultComponentAdapterFactory(monitor), parent);
        lifecycleStrategyForInstanceRegistrations = new DefaultLifecycleStrategy(monitor);
    }

    /**
      * Creates a new container with the DefaultComponentAdapterFactory using a
      * custom ComponentMonitor and lifecycle strategy
      *
      * @param monitor the ComponentMonitor to use
      * @param lifecycleStrategy the lifecycle strategy to use.
      * @param parent the parent container (used for component dependency lookups).
      */
    public DefaultPicoContainer(ComponentMonitor monitor, LifecycleStrategy lifecycleStrategy, PicoContainer parent) {
        this(new DefaultComponentAdapterFactory(monitor, lifecycleStrategy), lifecycleStrategy,  parent);
    }

    /**
      * Creates a new container with the DefaultComponentAdapterFactory using a
      * custom lifecycle strategy
      *
      * @param lifecycleStrategy the lifecycle strategy to use.
      * @param parent the parent container (used for component dependency lookups).
      */
    public DefaultPicoContainer(LifecycleStrategy lifecycleStrategy, PicoContainer parent) {
        this(new DefaultComponentMonitor(), lifecycleStrategy, parent);
    }


    /**
     * Creates a new container with a custom ComponentAdapterFactory and no parent container.
     *
     * @param componentAdapterFactory the ComponentAdapterFactory to use.
     */
    public DefaultPicoContainer(ComponentAdapterFactory componentAdapterFactory) {
        this(componentAdapterFactory, null);
    }

    /**
      * Creates a new container with the DefaultComponentAdapterFactory using a
      * custom ComponentMonitor
      *
      * @param monitor the ComponentMonitor to use
      */
    public DefaultPicoContainer(ComponentMonitor monitor) {
        this(monitor, new DefaultLifecycleStrategy(monitor), null);
    }

    /**
     * Creates a new container with a (caching) {@link org.picocontainer.defaults.DefaultComponentAdapterFactory}
     * and a parent container.
     *
     * @param parent the parent container (used for component dependency lookups).
     */
    public DefaultPicoContainer(PicoContainer parent) {
        this(new DefaultComponentAdapterFactory(), parent);
    }

    /**
     * Creates a new container with a (caching) {@link org.picocontainer.defaults.DefaultComponentAdapterFactory} and no parent container.
     */
    public DefaultPicoContainer() {
        this(new DefaultComponentAdapterFactory(), null);
    }

    public Collection getComponentAdapters() {
        return Collections.unmodifiableCollection(componentAdapters);
    }

    public final ComponentAdapter getComponentAdapter(Object componentKey) {
        ComponentAdapter adapter = (ComponentAdapter) componentKeyToAdapterCache.get(componentKey);
        if (adapter == null && parent != null) {
            adapter = parent.getComponentAdapter(componentKey);
        }
        return adapter;
    }

    public ComponentAdapter getComponentAdapterOfType(Class componentType) {
        // See http://jira.codehaus.org/secure/ViewIssue.jspa?key=PICO-115
        ComponentAdapter adapterByKey = getComponentAdapter(componentType);
        if (adapterByKey != null) {
            return adapterByKey;
        }

        List found = getComponentAdaptersOfType(componentType);

        if (found.size() == 1) {
            return ((ComponentAdapter) found.get(0));
        } else if (found.size() == 0) {
            if (parent != null) {
                return parent.getComponentAdapterOfType(componentType);
            } else {
                return null;
            }
        } else {
            Class[] foundClasses = new Class[found.size()];
            for (int i = 0; i < foundClasses.length; i++) {
                foundClasses[i] = ((ComponentAdapter) found.get(i)).getComponentImplementation();
            }

            throw new AmbiguousComponentResolutionException(componentType, foundClasses);
        }
    }

    public List getComponentAdaptersOfType(Class componentType) {
        if (componentType == null) {
            return Collections.EMPTY_LIST;
        }
        List found = new ArrayList();
        for (Iterator iterator = getComponentAdapters().iterator(); iterator.hasNext();) {
            ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();

            if (componentType.isAssignableFrom(componentAdapter.getComponentImplementation())) {
                found.add(componentAdapter);
            }
        }
        return found;
    }

    /**
     * {@inheritDoc}
     * This method can be used to override the ComponentAdapter created by the {@link org.picocontainer.defaults.ComponentAdapterFactory}
     * passed to the constructor of this container.
     */
    public ComponentAdapter registerComponent(ComponentAdapter componentAdapter) {
        Object componentKey = componentAdapter.getComponentKey();
        if (componentKeyToAdapterCache.containsKey(componentKey)) {
            throw new DuplicateComponentKeyRegistrationException(componentKey);
        }
        componentAdapters.add(componentAdapter);
        componentKeyToAdapterCache.put(componentKey, componentAdapter);
        return componentAdapter;
    }

    public ComponentAdapter unregisterComponent(Object componentKey) {
        ComponentAdapter adapter = (ComponentAdapter) componentKeyToAdapterCache.remove(componentKey);
        componentAdapters.remove(adapter);
        orderedComponentAdapters.remove(adapter);
        return adapter;
    }

    /**
     * {@inheritDoc}
     * The returned ComponentAdapter will be an {@link org.picocontainer.defaults.InstanceComponentAdapter}.
     */
    public ComponentAdapter registerComponentInstance(Object component) {
        return registerComponentInstance(component.getClass(), component);
    }

    /**
     * {@inheritDoc}
     * The returned ComponentAdapter will be an {@link org.picocontainer.defaults.InstanceComponentAdapter}.
     */
    public ComponentAdapter registerComponentInstance(Object componentKey, Object componentInstance) {
        ComponentAdapter componentAdapter = new InstanceComponentAdapter(componentKey, componentInstance, lifecycleStrategyForInstanceRegistrations);
        return registerComponent(componentAdapter);
    }

    /**
     * {@inheritDoc}
     * The returned ComponentAdapter will be instantiated by the {@link org.picocontainer.defaults.ComponentAdapterFactory}
     * passed to the container's constructor.
     */
    public ComponentAdapter registerComponentImplementation(Class componentImplementation) {
        return registerComponentImplementation(componentImplementation, componentImplementation);
    }

    /**
     * {@inheritDoc}
     * The returned ComponentAdapter will be instantiated by the {@link org.picocontainer.defaults.ComponentAdapterFactory}
     * passed to the container's constructor.
     */
    public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation) {
        return registerComponentImplementation(componentKey, componentImplementation, (Parameter[]) null);
    }

    /**
     * {@inheritDoc}
     * The returned ComponentAdapter will be instantiated by the {@link org.picocontainer.defaults.ComponentAdapterFactory}
     * passed to the container's constructor.
     */
    public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, Parameter[] parameters) {
        ComponentAdapter componentAdapter = componentAdapterFactory.createComponentAdapter(componentKey, componentImplementation, parameters);
        return registerComponent(componentAdapter);
    }

    /**
     * Same as {@link #registerComponentImplementation(Object, Class, org.picocontainer.Parameter[])}
     * but with parameters as a {@link java.util.List}. Makes it possible to use with Groovy arrays (which are actually Lists).
     */
    public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, List parameters) {
        Parameter[] parametersAsArray = (Parameter[]) parameters.toArray(new Parameter[parameters.size()]);
        return registerComponentImplementation(componentKey, componentImplementation, parametersAsArray);
    }

    private void addOrderedComponentAdapter(ComponentAdapter componentAdapter) {
        if (!orderedComponentAdapters.contains(componentAdapter)) {
            orderedComponentAdapters.add(componentAdapter);
        }
    }

    public List getComponentInstances() throws PicoException {
        return getComponentInstancesOfType(Object.class);
    }

    public List getComponentInstancesOfType(Class componentType) {
        if (componentType == null) {
            return Collections.EMPTY_LIST;
        }

        Map adapterToInstanceMap = new HashMap();
        for (Iterator iterator = componentAdapters.iterator(); iterator.hasNext();) {
            ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
            if (componentType.isAssignableFrom(componentAdapter.getComponentImplementation())) {
                Object componentInstance = getInstance(componentAdapter);
                adapterToInstanceMap.put(componentAdapter, componentInstance);

                // This is to ensure all are added. (Indirect dependencies will be added
                // from InstantiatingComponentAdapter).
                addOrderedComponentAdapter(componentAdapter);
            }
        }
        List result = new ArrayList();
        for (Iterator iterator = orderedComponentAdapters.iterator(); iterator.hasNext();) {
            Object componentAdapter = iterator.next();
            final Object componentInstance = adapterToInstanceMap.get(componentAdapter);
            if (componentInstance != null) {
                // may be null in the case of the "implicit" adapter
                // representing "this".
                result.add(componentInstance);
            }
        }
        return result;
    }

    public Object getComponentInstance(Object componentKey) {
        ComponentAdapter componentAdapter = getComponentAdapter(componentKey);
        if (componentAdapter != null) {
            return getInstance(componentAdapter);
        } else {
            return null;
        }
    }

    public Object getComponentInstanceOfType(Class componentType) {
        final ComponentAdapter componentAdapter = getComponentAdapterOfType(componentType);
        return componentAdapter == null ? null : getInstance(componentAdapter);
    }

    private Object getInstance(ComponentAdapter componentAdapter) {
        // check wether this is our adapter
        // we need to check this to ensure up-down dependencies cannot be followed
        final boolean isLocal = componentAdapters.contains(componentAdapter);

        if (isLocal) {
            PicoException firstLevelException = null;
            Object instance = null;
            try {
                instance = componentAdapter.getComponentInstance(this);
            } catch (PicoInitializationException e) {
                firstLevelException = e;
            } catch (PicoIntrospectionException e) {
                firstLevelException = e;
            }
            if (firstLevelException != null) {
                if (parent != null) {
                    instance = parent.getComponentInstance(componentAdapter.getComponentKey());
                    if( instance != null ) {
                        return instance;
                    }
                }

                throw firstLevelException;
            }
            addOrderedComponentAdapter(componentAdapter);

            return instance;
        } else if (parent != null) {
            return parent.getComponentInstance(componentAdapter.getComponentKey());
        }

        return null;
    }


    public PicoContainer getParent() {
        return parent;
    }

    public ComponentAdapter unregisterComponentByInstance(Object componentInstance) {
        Collection componentAdapters = getComponentAdapters();
        for (Iterator iterator = componentAdapters.iterator(); iterator.hasNext();) {
            ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
            if (getInstance(componentAdapter).equals(componentInstance)) {
                return unregisterComponent(componentAdapter.getComponentKey());
            }
        }
        return null;
    }

    /**
     * @deprecated since 1.1 - Use "new VerifyingVisitor().traverse(this)"
     */
    public void verify() throws PicoVerificationException {
        new VerifyingVisitor().traverse(this);
    }

    /**
     * Start the components of this PicoContainer and all its logical child containers.
     * The starting of the child container is only attempted if the parent
     * container start successfully.  The child container for which start is attempted
     * is tracked so that upon stop, only those need to be stopped.
     * The lifecycle operation is delegated to the component adapter,
     * if it is an instance of {@link org.picocontainer.LifecycleManager lifecycle manager}.
     * The actual {@link org.picocontainer.defaults.LifecycleStrategy lifecycle strategy} supported
     * depends on the concrete implementation of the adapter.
     *
     * @see org.picocontainer.LifecycleManager
     * @see org.picocontainer.defaults.LifecycleStrategy
     * @see #makeChildContainer()
     * @see #addChildContainer(org.picocontainer.PicoContainer)
     * @see #removeChildContainer(org.picocontainer.PicoContainer)
     */
    public void start() {
        if (disposed) throw new IllegalStateException("Already disposed");
        if (started) throw new IllegalStateException("Already started");
        started = true;
        this.lifecycleManager.start(this);
        childrenStarted.clear();
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            PicoContainer child = (PicoContainer) iterator.next();
            childrenStarted.add(new Integer(child.hashCode()));
            child.start();
        }
    }

    /**
     * Stop the components of this PicoContainer and all its logical child containers.
     * The stopping of the child containers is only attempted for those that have been
     * started, possibly not successfully.
     * The lifecycle operation is delegated to the component adapter,
     * if it is an instance of {@link org.picocontainer.LifecycleManager lifecycle manager}.
     * The actual {@link org.picocontainer.defaults.LifecycleStrategy lifecycle strategy} supported
     * depends on the concrete implementation of the adapter.
     *
     * @see org.picocontainer.LifecycleManager
     * @see org.picocontainer.defaults.LifecycleStrategy
     * @see #makeChildContainer()
     * @see #addChildContainer(org.picocontainer.PicoContainer)
     * @see #removeChildContainer(org.picocontainer.PicoContainer)
     */
    public void stop() {
        if (disposed) throw new IllegalStateException("Already disposed");
        if (!started) throw new IllegalStateException("Not started");
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            PicoContainer child = (PicoContainer) iterator.next();
            if ( childStarted(child) ){
                child.stop();
            }
        }
        this.lifecycleManager.stop(this);
        started = false;
    }

    /**
     * Checks the status of the child container to see if it's been started
     * to prevent IllegalStateException upon stop
     * @param child the child PicoContainer
     * @return A boolean, <code>true</code> if the container is started
     */
    private boolean childStarted(PicoContainer child) {
        return childrenStarted.contains(new Integer(child.hashCode()));
    }

    /**
     * Dispose the components of this PicoContainer and all its logical child containers.
     * The lifecycle operation is delegated to the component adapter,
     * if it is an instance of {@link org.picocontainer.LifecycleManager lifecycle manager}.
     * The actual {@link org.picocontainer.defaults.LifecycleStrategy lifecycle strategy} supported
     * depends on the concrete implementation of the adapter.
     *
     * @see org.picocontainer.LifecycleManager
     * @see org.picocontainer.defaults.LifecycleStrategy
     * @see #makeChildContainer()
     * @see #addChildContainer(org.picocontainer.PicoContainer)
     * @see #removeChildContainer(org.picocontainer.PicoContainer)
     */
    public void dispose() {
        if (disposed) throw new IllegalStateException("Already disposed");
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            PicoContainer child = (PicoContainer) iterator.next();
            child.dispose();
        }
        this.lifecycleManager.dispose(this);
        disposed = true;
    }

    public MutablePicoContainer makeChildContainer() {
        org.picocontainer.defaults.DefaultPicoContainer pc = new org.picocontainer.defaults.DefaultPicoContainer(componentAdapterFactory,
                                                           lifecycleStrategyForInstanceRegistrations,
                                                           this);
        addChildContainer(pc);
        return pc;
    }

    public boolean addChildContainer(PicoContainer child) {
        if (children.add(child)) {
            // @todo Should only be added if child container has also be started
            if (started) {
                childrenStarted.add(new Integer(child.hashCode()));
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean removeChildContainer(PicoContainer child) {
        final boolean result = children.remove(child);
        return result;
    }

    public void accept(PicoVisitor visitor) {
        visitor.visitContainer(this);
        final List componentAdapters = new ArrayList(getComponentAdapters());
        for (Iterator iterator = componentAdapters.iterator(); iterator.hasNext();) {
            ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
            componentAdapter.accept(visitor);
        }
        final List allChildren = new ArrayList(children);
        for (Iterator iterator = allChildren.iterator(); iterator.hasNext();) {
            PicoContainer child = (PicoContainer) iterator.next();
            child.accept(visitor);
        }
    }

    /**
     * Changes monitor in the ComponentAdapterFactory, the component adapters
     * and the child containers, if these support a ComponentMonitorStrategy.
     * {@inheritDoc}
     */
    public void changeMonitor(ComponentMonitor monitor) {
        // will also change monitor in lifecycleStrategyForInstanceRegistrations
        if (componentAdapterFactory instanceof ComponentMonitorStrategy) {
            ((ComponentMonitorStrategy) componentAdapterFactory).changeMonitor(monitor);
        }
        for ( Iterator i = componentAdapters.iterator(); i.hasNext(); ){
            Object adapter = i.next();
            if ( adapter instanceof ComponentMonitorStrategy ) {
                ((ComponentMonitorStrategy)adapter).changeMonitor(monitor);
            }
        }
        for (Iterator i = children.iterator(); i.hasNext();) {
            Object child = i.next();
            if (child instanceof ComponentMonitorStrategy) {
                ((ComponentMonitorStrategy) child).changeMonitor(monitor);
            }
        }
    }

    /**
     * Returns the first current monitor found in the ComponentAdapterFactory, the component adapters
     * and the child containers, if these support a ComponentMonitorStrategy.
     * {@inheritDoc}
     * @throws org.picocontainer.PicoIntrospectionException if no component monitor is found in container or its children
     */
    public ComponentMonitor currentMonitor() {
        if (componentAdapterFactory instanceof ComponentMonitorStrategy) {
            return ((ComponentMonitorStrategy) componentAdapterFactory).currentMonitor();
        }
        for ( Iterator i = componentAdapters.iterator(); i.hasNext(); ){
            Object adapter = i.next();
            if ( adapter instanceof ComponentMonitorStrategy ) {
                return ((ComponentMonitorStrategy)adapter).currentMonitor();
            }
        }
        for (Iterator i = children.iterator(); i.hasNext();) {
            Object child = i.next();
            if (child instanceof ComponentMonitorStrategy) {
                return ((ComponentMonitorStrategy) child).currentMonitor();
            }
        }
        throw new PicoIntrospectionException("No component monitor found in container or its children");
    }

   /**
    * <p>
    * Implementation of lifecycle manager which delegates to the container's component adapters.
    * The component adapters will be ordered by dependency as registered in the container.
    * This LifecycleManager will delegate calls on the lifecycle methods to the component adapters
    * if these are themselves LifecycleManagers.
    * </p>
    *
    * @author Mauro Talevi
    * @since 1.2
    */
    private class OrderedComponentAdapterLifecycleManager implements LifecycleManager, Serializable {

        /** List collecting the CAs which have been successfully started */
        private List startedComponentAdapters = new ArrayList();

        /**
         * {@inheritDoc}
         * Loops over all component adapters and invokes
         * start(PicoContainer) method on the ones which are LifecycleManagers
         */
        public void start(PicoContainer node) {
            Collection adapters = getComponentAdapters();
            for (final Iterator iter = adapters.iterator(); iter.hasNext();) {
                final ComponentAdapter adapter = (ComponentAdapter)iter.next();
                if ( adapter instanceof LifecycleManager ){
                    LifecycleManager manager = (LifecycleManager)adapter;
                    if (manager.hasLifecycle()) {
                        // create an instance, it will be added to the ordered CA list
                        adapter.getComponentInstance(node);
                        addOrderedComponentAdapter(adapter);
                    }
                }
            }
            adapters = orderedComponentAdapters;
            // clear list of started CAs
            startedComponentAdapters.clear();
            for (final Iterator iter = adapters.iterator(); iter.hasNext();) {
                final Object adapter = iter.next();
                if ( adapter instanceof LifecycleManager ){
                    LifecycleManager manager = (LifecycleManager)adapter;
                    manager.start(node);
                    startedComponentAdapters.add(adapter);
                }
            }
        }

        /**
         * {@inheritDoc}
         * Loops over started component adapters (in inverse order) and invokes
         * stop(PicoContainer) method on the ones which are LifecycleManagers
         */
        public void stop(PicoContainer node) {
            List adapters = startedComponentAdapters;
            for (int i = adapters.size() - 1; 0 <= i; i--) {
                Object adapter = adapters.get(i);
                if ( adapter instanceof LifecycleManager ){
                    LifecycleManager manager = (LifecycleManager)adapter;
                    manager.stop(node);
                }
            }
        }

        /**
         * {@inheritDoc}
         * Loops over all component adapters (in inverse order) and invokes
         * dispose(PicoContainer) method on the ones which are LifecycleManagers
         */
        public void dispose(PicoContainer node) {
            List adapters = orderedComponentAdapters;
            for (int i = adapters.size() - 1; 0 <= i; i--) {
                Object adapter = adapters.get(i);
                if ( adapter instanceof LifecycleManager ){
                    LifecycleManager manager = (LifecycleManager)adapter;
                    manager.dispose(node);
                }
            }
        }

        public boolean hasLifecycle() {
            throw new UnsupportedOperationException("Should not have been called");
        }

    }

}