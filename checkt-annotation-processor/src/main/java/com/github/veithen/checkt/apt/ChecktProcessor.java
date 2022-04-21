/*-
 * #%L
 * Checkt
 * %%
 * Copyright (C) 2020 - 2022 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.checkt.apt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;

@AutoService(Processor.class)
public class ChecktProcessor extends AbstractProcessor {
    private static final String TYPE_TOKEN_ANNOTATION_NAME =
            "com.github.veithen.checkt.annotation.TypeToken";
    private static final String CONTAINER_ANNOTATION_NAME =
            "com.github.veithen.checkt.annotation.Container";

    private void writeSource(
            CharSequence name,
            Collection<? extends Element> originatingElements,
            SourceProvider sourceProvider) {
        try (SourceWriter out =
                new SourceWriter(
                        processingEnv
                                .getFiler()
                                .createSourceFile(
                                        name,
                                        originatingElements.toArray(
                                                new Element[originatingElements.size()]))
                                .openWriter())) {
            sourceProvider.writeTo(out);
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write source file");
        }
    }

    static String formatTypeParameter(TypeParameterElement typeParameter) {
        List<? extends TypeMirror> bounds = typeParameter.getBounds();
        if (bounds.isEmpty()) {
            return typeParameter.toString();
        } else {
            return typeParameter
                    + " extends "
                    + bounds.stream().map(Object::toString).collect(Collectors.joining(" & "));
        }
    }

    static String formatTypeParameters(List<? extends TypeParameterElement> params) {
        return params.stream()
                .map(ChecktProcessor::formatTypeParameter)
                .collect(Collectors.joining(",", "<", ">"));
    }

    static String getTokenName(ExecutableElement method, boolean lowerCase) {
        String name = method.getSimpleName().toString();
        if (name.startsWith("get")) {
            name = name.substring(3);
        }
        if (lowerCase) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
        return name;
    }

    private void generateCastMethod(
            SourceWriter out, TypeElement type, List<ExecutableElement> methods, String name)
            throws IOException {
        Set<TypeParameterElement> constrainedTypeParameters = new HashSet<>();
        for (ExecutableElement method : methods) {
            TypeMirror returnType = method.getReturnType();
            if (!(returnType instanceof DeclaredType)) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                Kind.ERROR,
                                "Methods annotated with @TypeToken must return a reference",
                                method);
                return;
            }
            DeclaredType declaredType = (DeclaredType) returnType;
            boolean valid = false;
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                if (typeArgument instanceof TypeVariable) {
                    constrainedTypeParameters.add(
                            (TypeParameterElement) ((TypeVariable) typeArgument).asElement());
                    valid = true;
                }
            }
            if (!valid) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                Kind.ERROR, "Method does not return a valid type token", method);
                return;
            }
        }
        out.println();
        out.print("    ");
        if (type.getModifiers().contains(Modifier.PUBLIC)) {
            out.print("public ");
        }
        out.print("static ");
        out.print(formatTypeParameters(type.getTypeParameters()));
        out.print(" ");
        String returnType =
                type.getSimpleName()
                        + type.getTypeParameters().stream()
                                .map(TypeParameterElement::getSimpleName)
                                .collect(Collectors.joining(",", "<", ">"));
        out.print(returnType);
        out.print(" ");
        out.print(name);
        out.print("(");
        out.print(type.getSimpleName());
        out.print("<");
        out.print(
                type.getTypeParameters().stream()
                        .map(t -> constrainedTypeParameters.contains(t) ? "?" : t.getSimpleName())
                        .collect(Collectors.joining(",")));
        out.print("> o");
        for (ExecutableElement method : methods) {
            out.print(", ");
            out.print(method.getReturnType());
            out.print(" ");
            out.print(getTokenName(method, true));
        }
        out.println(") {");
        for (ExecutableElement method : methods) {
            String tokenName = getTokenName(method, true);
            out.print("        if (");
            out.print(tokenName);
            out.print(" == null || o.");
            out.print(method.getSimpleName());
            out.print("() != ");
            out.print(tokenName);
            out.println(") {");
            out.println("            throw new ClassCastException();");
            out.println("        }");
        }
        out.print("        return (");
        out.print(returnType);
        out.println(")o;");
        out.println("    }");
    }

    private void generateSafeCast(TypeElement annotation, RoundEnvironment env) {
        Map<PackageElement, Map<TypeElement, List<ExecutableElement>>> packageMap = new HashMap<>();
        for (Element element : env.getElementsAnnotatedWith(annotation)) {
            if (!(element instanceof ExecutableElement)) {
                processingEnv
                        .getMessager()
                        .printMessage(Kind.ERROR, "Unexpected @TypeToken", element);
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            TypeElement type = (TypeElement) method.getEnclosingElement();
            PackageElement pkg = (PackageElement) type.getEnclosingElement();
            packageMap
                    .computeIfAbsent(
                            pkg,
                            k ->
                                    new TreeMap<>(
                                            (o1, o2) ->
                                                    o1.getSimpleName()
                                                            .toString()
                                                            .compareTo(
                                                                    o2.getSimpleName().toString())))
                    .computeIfAbsent(type, k -> new ArrayList<>())
                    .add(method);
        }
        for (Map.Entry<PackageElement, Map<TypeElement, List<ExecutableElement>>> packageEntry :
                packageMap.entrySet()) {
            PackageElement pkg = packageEntry.getKey();
            writeSource(
                    pkg.getQualifiedName() + ".SafeCast",
                    packageEntry.getValue().keySet(),
                    out -> {
                        out.print("package ");
                        out.print(pkg.getQualifiedName());
                        out.println(";");
                        out.println();
                        if (packageEntry.getValue().keySet().stream()
                                .anyMatch(t -> t.getModifiers().contains(Modifier.PUBLIC))) {
                            out.print("public ");
                        }
                        out.println("final class SafeCast {");
                        out.println("    private SafeCast() {}");
                        for (Map.Entry<TypeElement, List<ExecutableElement>> typeEntry :
                                packageEntry.getValue().entrySet()) {
                            TypeElement type = typeEntry.getKey();
                            List<ExecutableElement> methods = typeEntry.getValue();
                            generateCastMethod(out, type, methods, "cast");
                            if (methods.size() > 1) {
                                for (ExecutableElement method : methods) {
                                    generateCastMethod(
                                            out,
                                            type,
                                            Collections.singletonList(method),
                                            "castBy" + getTokenName(method, false));
                                }
                            }
                        }
                        out.println("}");
                    });
        }
    }

    private void generateContainer(TypeElement annotation, TypeElement element) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                element.getAnnotationMirrors().stream()
                        .filter(a -> a.getAnnotationType().asElement() == annotation)
                        .findFirst()
                        .get()
                        .getElementValues();
        String className =
                (String)
                        values.entrySet().stream()
                                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                                .findFirst()
                                .get()
                                .getValue()
                                .getValue();
        String typeParameters = formatTypeParameters(element.getTypeParameters());
        String commonModifiers = element.getModifiers().contains(Modifier.PUBLIC) ? "public " : "";
        List<? extends TypeMirror> typeArguments =
                ((DeclaredType) element.getSuperclass()).getTypeArguments();
        TypeMirror keyType = typeArguments.get(0);
        TypeMirror valueType = typeArguments.get(1);
        PackageElement pkg = (PackageElement) element.getEnclosingElement();
        writeSource(
                pkg.getQualifiedName() + "." + className,
                Collections.singleton(element),
                out -> {
                    out.print("package ");
                    out.print(pkg.getQualifiedName());
                    out.println(";");
                    out.println();
                    out.println("import java.util.Map;");
                    out.println("import java.util.IdentityHashMap;");
                    out.println();
                    out.print(commonModifiers);
                    out.print("final class ");
                    out.print(className);
                    out.println(" {");
                    out.println("    private final Map map = new IdentityHashMap();");
                    out.println();
                    out.print("    ");
                    out.print(commonModifiers);
                    out.print(typeParameters);
                    out.print(" ");
                    out.print(valueType);
                    out.print(" put(");
                    out.print(keyType);
                    out.print(" key, ");
                    out.print(valueType);
                    out.println(" value) {");
                    out.print("        return (");
                    out.print(valueType);
                    out.println(")map.put(key, value);");
                    out.println("    }");
                    out.println();
                    out.print("    ");
                    out.print(commonModifiers);
                    out.print(typeParameters);
                    out.print(" ");
                    out.print(valueType);
                    out.print(" get(");
                    out.print(keyType);
                    out.println(" key) {");
                    out.print("        return (");
                    out.print(valueType);
                    out.println(")map.get(key);");
                    out.println("    }");
                    out.println("}");
                });
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().contentEquals(TYPE_TOKEN_ANNOTATION_NAME)) {
                generateSafeCast(annotation, env);
            } else if (annotation.getQualifiedName().contentEquals(CONTAINER_ANNOTATION_NAME)) {
                for (Element element : env.getElementsAnnotatedWith(annotation)) {
                    generateContainer(annotation, (TypeElement) element);
                }
            }
        }
        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Arrays.asList(TYPE_TOKEN_ANNOTATION_NAME, CONTAINER_ANNOTATION_NAME));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
