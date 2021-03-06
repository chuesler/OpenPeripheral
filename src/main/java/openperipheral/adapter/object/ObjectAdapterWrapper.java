package openperipheral.adapter.object;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import openperipheral.adapter.*;
import openperipheral.adapter.PropertyListBuilder.FieldContext;
import openperipheral.adapter.PropertyListBuilder.IPropertyExecutorFactory;
import openperipheral.adapter.PropertyListBuilder.PropertyExecutor;
import openperipheral.adapter.method.MethodDeclaration;
import openperipheral.api.IObjectAdapter;
import dan200.computercraft.api.lua.ILuaContext;

public abstract class ObjectAdapterWrapper extends AdapterWrapper<IObjectMethodExecutor> {

	protected ObjectAdapterWrapper(Class<?> adapterClass, Class<?> targetClass) {
		super(adapterClass, targetClass);
	}

	private static final String ARG_TARGET = "target";
	private static final String ARG_CONTEXT = "context";

	@Override
	protected List<IObjectMethodExecutor> buildMethodList() {
		return buildMethodList(true, new MethodExecutorFactory<IObjectMethodExecutor>() {
			@Override
			public IObjectMethodExecutor createExecutor(Method method, final MethodDeclaration decl, final Map<String, Method> proxyArgs) {
				return new IObjectMethodExecutor() {

					@Override
					public IDescriptable getWrappedMethod() {
						return decl;
					}

					@Override
					public Object[] execute(ILuaContext context, Object target, Object[] args) throws Exception {
						return createWrapper(decl, context, target, args, proxyArgs).call();
					}

					@Override
					public boolean isSynthetic() {
						return false;
					}
				};
			}
		});
	}

	protected abstract Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args, Map<String, Method> proxyArgs);

	private static class ObjectPropertyExecutor extends PropertyExecutor implements IObjectMethodExecutor {

		protected ObjectPropertyExecutor(FieldContext context) {
			super(context);
		}

		@Override
		public Object[] execute(ILuaContext context, Object target, Object[] args) {
			return call(target, args);
		}

	}

	public static class External extends ObjectAdapterWrapper {

		private final IObjectAdapter adapter;

		public External(IObjectAdapter adapter) {
			super(adapter.getClass(), adapter.getTargetClass());
			this.adapter = adapter;
		}

		@Override
		protected void nameDefaultParameters(MethodDeclaration decl) {
			decl.nameJavaArg(0, ARG_TARGET);
			decl.nameJavaArg(1, ARG_CONTEXT);
		}

		@Override
		protected void validateArgTypes(MethodDeclaration decl) {
			decl.declareJavaArgType(ARG_CONTEXT, ILuaContext.class);
			decl.declareJavaArgType(ARG_TARGET, targetCls);
		}

		@Override
		protected Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args, Map<String, Method> proxyArgs) {
			return nameAdapterMethods(target, proxyArgs, decl.createWrapper(adapter)
					.setJavaArg(ARG_TARGET, target)
					.setJavaArg(ARG_CONTEXT, context)
					.setLuaArgs(args));
		}
	}

	public static class Inline extends ObjectAdapterWrapper implements IPropertyExecutorFactory<IObjectMethodExecutor> {

		public Inline(Class<?> targetClass) {
			super(targetClass, targetClass);
		}

		@Override
		protected void nameDefaultParameters(MethodDeclaration decl) {
			decl.nameJavaArg(0, ARG_CONTEXT);
		}

		@Override
		protected void validateArgTypes(MethodDeclaration decl) {
			decl.declareJavaArgType(ARG_CONTEXT, ILuaContext.class);
		}

		@Override
		protected Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args, Map<String, Method> proxyArgs) {
			return decl.createWrapper(target)
					.setJavaArg(ARG_CONTEXT, context)
					.setLuaArgs(args);
		}

		@Override
		public IObjectMethodExecutor createExecutor(FieldContext context) {
			return new ObjectPropertyExecutor(context);
		}

		@Override
		protected List<IObjectMethodExecutor> buildMethodList() {
			List<IObjectMethodExecutor> result = super.buildMethodList();
			PropertyListBuilder.buildPropertyList(targetCls, this, result);
			return result;
		}
	}
}
