package openperipheral;

import java.lang.reflect.Modifier;
import java.util.*;

import openperipheral.adapter.AdapterFactoryWrapper;
import openperipheral.adapter.AdapterRegistryWrapper;
import openperipheral.api.IApiInterface;
import openperipheral.meta.EntityMetadataBuilder;
import openperipheral.meta.ItemStackMetadataBuilder;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ApiProvider {

	private interface IApiProvider {
		public IApiInterface getInterface();
	}

	private static class SingleInstanceProvider implements IApiProvider {
		private final IApiInterface instance;

		public SingleInstanceProvider(Class<? extends IApiInterface> cls) {
			try {
				instance = cls.newInstance();
			} catch (Throwable t) {
				throw Throwables.propagate(t);
			}
		}

		@Override
		public IApiInterface getInterface() {
			return instance;
		}
	}

	private static class NewInstanceProvider implements IApiProvider {
		private final Class<? extends IApiInterface> cls;

		public NewInstanceProvider(Class<? extends IApiInterface> cls) {
			this.cls = cls;
		}

		@Override
		public IApiInterface getInterface() {
			try {
				return cls.newInstance();
			} catch (Throwable t) {
				throw Throwables.propagate(t);
			}
		}
	}

	private static class SingletonProvider implements IApiProvider {
		private final IApiInterface obj;

		public SingletonProvider(IApiInterface obj) {
			this.obj = obj;
		}

		@Override
		public IApiInterface getInterface() {
			return obj;
		}
	}

	private static final Map<Class<? extends IApiInterface>, IApiProvider> PROVIDERS = Maps.newHashMap();

	@SuppressWarnings("unchecked")
	private static void addAllApis(Collection<Class<? extends IApiInterface>> output, Class<?>... intfs) {
		for (Class<?> cls : intfs) {
			Preconditions.checkArgument(cls.isInterface());
			if (cls != IApiInterface.class &&
					IApiInterface.class.isAssignableFrom(cls)) output.add((Class<? extends IApiInterface>)cls);
		}
	}

	private static void addAllInterfaces(Set<Class<? extends IApiInterface>> interfaces) {
		Queue<Class<? extends IApiInterface>> queue = Lists.newLinkedList(interfaces);

		Class<? extends IApiInterface> cls;
		while ((cls = queue.poll()) != null) {
			interfaces.add(cls);
			addAllApis(queue, cls.getInterfaces());
		}
	}

	private static void registerInterfaces(Class<? extends IApiInterface> cls, IApiProvider provider, final boolean includeSuper) {
		Set<Class<? extends IApiInterface>> implemented = Sets.newHashSet();
		addAllApis(implemented, cls.getInterfaces());
		if (includeSuper) addAllInterfaces(implemented);

		for (Class<? extends IApiInterface> impl : implemented) {
			IApiProvider prev = PROVIDERS.put(impl, provider);
			Preconditions.checkState(prev == null, "Conflict on interface %s", impl);
		}
	}

	private static void registerClass(Class<? extends IApiInterface> cls) {
		Preconditions.checkArgument(!Modifier.isAbstract(cls.getModifiers()));

		ApiImplementation meta = cls.getAnnotation(ApiImplementation.class);
		Preconditions.checkNotNull(meta);

		IApiProvider provider = meta.cacheable()? new SingleInstanceProvider(cls) : new NewInstanceProvider(cls);
		registerInterfaces(cls, provider, meta.includeSuper());
	}

	private static void registerInstance(IApiInterface obj) {
		final Class<? extends IApiInterface> cls = obj.getClass();

		ApiSingleton meta = cls.getAnnotation(ApiSingleton.class);
		Preconditions.checkNotNull(meta);

		IApiProvider provider = new SingletonProvider(obj);
		registerInterfaces(cls, provider, meta.includeSuper());
	}

	static {
		registerClass(AdapterFactoryWrapper.class);
		registerClass(AdapterRegistryWrapper.class);
		registerClass(EntityMetadataBuilder.class);
		registerClass(ItemStackMetadataBuilder.class);

		registerInstance(TypeConversionRegistry.INSTANCE);
	}

	public static IApiInterface provideImplementation(Class<? extends IApiInterface> cls) {
		IApiProvider provider = PROVIDERS.get(cls);
		Preconditions.checkNotNull(provider, "Can't get implementation for class %s", cls);
		return provider.getInterface();
	}

}
