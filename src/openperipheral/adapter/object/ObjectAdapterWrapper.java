package openperipheral.adapter.object;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;

import openperipheral.adapter.AdapterWrapper;
import openperipheral.adapter.MethodDeclaration;
import openperipheral.api.IObjectAdapter;
import dan200.computer.api.ILuaContext;

public abstract class ObjectAdapterWrapper extends AdapterWrapper<IObjectMethodExecutor> {

	protected ObjectAdapterWrapper(Class<?> adapterClass, Class<?> targetClass) {
		super(adapterClass, targetClass);
	}

	private static final String ARG_TARGET = "target";
	private static final String ARG_CONTEXT = "context";

	@Override
	protected Map<String, IObjectMethodExecutor> buildMethodMap() {
		return buildMethodMap(true, new MethodExecutorFactory<IObjectMethodExecutor>() {

			@Override
			public IObjectMethodExecutor createExecutor(Method method, final MethodDeclaration decl) {
				return new IObjectMethodExecutor() {

					@Override
					public MethodDeclaration getWrappedMethod() {
						return decl;
					}

					@Override
					public Object[] execute(ILuaContext context, Object target, Object[] args) throws Exception {
						return createWrapper(decl, context, target, args).call();
					}

					@Override
					public boolean isSynthetic() {
						return false;
					}
				};
			}
		});
	}

	protected abstract Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args);

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
			decl.checkJavaArgType(ARG_CONTEXT, ILuaContext.class);
			decl.checkJavaArgType(ARG_TARGET, targetCls);
		}

		@Override
		protected Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args) {
			return decl.createWrapper(adapter)
					.setJavaArg(ARG_TARGET, target)
					.setJavaArg(ARG_CONTEXT, context)
					.setLuaArgs(args);
		}

	}

	public static class Inline extends ObjectAdapterWrapper {

		public Inline(Class<?> targetClass) {
			super(targetClass, targetClass);
		}

		@Override
		protected void nameDefaultParameters(MethodDeclaration decl) {
			decl.nameJavaArg(0, ARG_CONTEXT);
		}

		@Override
		protected void validateArgTypes(MethodDeclaration decl) {
			decl.checkJavaArgType(ARG_CONTEXT, ILuaContext.class);
		}

		@Override
		protected Callable<Object[]> createWrapper(MethodDeclaration decl, ILuaContext context, Object target, Object[] args) {
			return decl.createWrapper(target)
					.setJavaArg(ARG_CONTEXT, context)
					.setLuaArgs(args);
		}

	}
}
