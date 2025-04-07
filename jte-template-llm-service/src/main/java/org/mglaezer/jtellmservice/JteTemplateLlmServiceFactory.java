package org.mglaezer.jtellmservice;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class JteTemplateLlmServiceFactory<ResponseType> {

    private final Function<String, ResponseType> llmResponseProvider;
    private final TemplateEngine templateEngine;

    public JteTemplateLlmServiceFactory(Function<String, ResponseType> llmResponseProvider, CodeResolver codeResolver) {
        this.llmResponseProvider = llmResponseProvider;
        this.templateEngine = TemplateEngine.create(codeResolver, ContentType.Plain);
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> serviceInterface) {
        validateServiceInterface(serviceInterface);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] {serviceInterface},
                new JteTemplateLlmInvocationHandler(serviceInterface));
    }

    private <T> void validateServiceInterface(Class<T> serviceInterface) {
        String packagePath = serviceInterface.getPackage().getName().replace('.', '/');

        for (Method method : serviceInterface.getDeclaredMethods()) {
            // Skip Object methods like equals, hashCode, toString
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            PromptTemplate promptAnnotation = method.getAnnotation(PromptTemplate.class);
            if (promptAnnotation == null) {
                throw new IllegalArgumentException("Method " + method.getName() + " in "
                        + serviceInterface.getSimpleName() + " must be annotated with @"
                        + PromptTemplate.class.getSimpleName());
            }

            // Continue with existing parameter validation
            String templatePath = packagePath + "/" + promptAnnotation.fileName();
            Map<String, Class<?>> templateParams = templateEngine.getParamInfo(templatePath);
            Map<String, Parameter> declaredParams = new HashMap<>();

            // ... rest of the validation code remains the same

            // Collect all parameters declared in the method
            for (Parameter parameter : method.getParameters()) {
                PromptParam paramAnnotation = parameter.getAnnotation(PromptParam.class);
                if (paramAnnotation != null) {
                    declaredParams.put(paramAnnotation.value(), parameter);
                }
            }

            // Check for missing or extra parameters
            Set<String> missingParams = new HashSet<>(templateParams.keySet());
            missingParams.removeAll(declaredParams.keySet());

            Set<String> extraParams = new HashSet<>(declaredParams.keySet());
            extraParams.removeAll(templateParams.keySet());

            if (!missingParams.isEmpty() || !extraParams.isEmpty()) {
                StringBuilder err =
                        new StringBuilder("Template parameter mismatch for " + serviceInterface.getSimpleName() + "."
                                + method.getName() + "() with template " + templatePath + ":");
                if (!missingParams.isEmpty()) {
                    err.append("\n  Missing required parameters: ").append(String.join(", ", missingParams));
                }
                if (!extraParams.isEmpty()) {
                    err.append("\n  Extra parameters provided: ").append(String.join(", ", extraParams));
                }
                throw new IllegalArgumentException(err.toString());
            }
        }
    }

    private class JteTemplateLlmInvocationHandler implements InvocationHandler {
        private final Class<?> serviceInterface;

        private JteTemplateLlmInvocationHandler(Class<?> serviceInterface) {
            this.serviceInterface = serviceInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            String prompt = preparePrompt(method, args, serviceInterface.getPackage());
            return llmResponseProvider.apply(prompt);
        }
    }

    private String preparePrompt(Method method, Object[] args, Package pkg) {
        String packagePath = pkg.getName().replace('.', '/');
        PromptTemplate promptAnnotation = method.getAnnotation(PromptTemplate.class);
        String templatePath = packagePath + "/" + promptAnnotation.fileName();

        Map<String, Object> params = new HashMap<>();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            PromptParam paramAnnotation = parameters[i].getAnnotation(PromptParam.class);
            if (paramAnnotation != null) {
                params.put(paramAnnotation.value(), args[i]);
            }
        }

        StringOutput output = new StringOutput();
        templateEngine.render(templatePath, params, output);
        return output.toString();
    }
}
