/*-
 * #%L
 * Checkt
 * %%
 * Copyright (C) 2020 Andreas Veithen
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
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
    private static final String TYPE_TOKEN_ANNOTATION_NAME = "com.github.veithen.checkt.annotation.TypeToken";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().contentEquals(TYPE_TOKEN_ANNOTATION_NAME)) {
                Map<PackageElement,Map<TypeElement,List<ExecutableElement>>> packageMap = new HashMap<>();
                for (Element element : env.getElementsAnnotatedWith(annotation)) {
                    if (!(element instanceof ExecutableElement)) {
                        processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected @TypeToken", element);
                        continue;
                    }
                    ExecutableElement method = (ExecutableElement)element;
                    TypeElement type = (TypeElement)method.getEnclosingElement();
                    PackageElement pkg = (PackageElement)type.getEnclosingElement();
                    packageMap.computeIfAbsent(pkg, k -> new HashMap<>()).computeIfAbsent(type, k -> new ArrayList<>()).add(method);
                }
                for (Map.Entry<PackageElement,Map<TypeElement,List<ExecutableElement>>> packageEntry : packageMap.entrySet()) {
                    PackageElement pkg = packageEntry.getKey();
                    try (PrintWriter out = new PrintWriter(processingEnv.getFiler().createSourceFile(pkg.getQualifiedName() + ".SafeCast", packageEntry.getValue().keySet().toArray(new TypeElement[0])).openWriter())) {
                        out.print("package ");
                        out.print(pkg.getQualifiedName());
                        out.println(";");
                        out.println();
                        if (packageEntry.getValue().keySet().stream().anyMatch(t -> t.getModifiers().contains(Modifier.PUBLIC))) {
                            out.print("public ");
                        }
                        out.println("final class SafeCast {");
                        out.println("    private SafeCast() {}");
                        out.println();
                        typeLoop: for (Map.Entry<TypeElement,List<ExecutableElement>> typeEntry : packageEntry.getValue().entrySet()) {
                            TypeElement type = typeEntry.getKey();
                            Set<TypeParameterElement> constrainedTypeParameters = new HashSet<>();
                            for (ExecutableElement method : typeEntry.getValue()) {
                                TypeMirror returnType = method.getReturnType();
                                if (!(returnType instanceof DeclaredType)) {
                                    processingEnv.getMessager().printMessage(Kind.ERROR, "Methods annotated with @TypeToken must return a reference", method);
                                    continue typeLoop;
                                }
                                DeclaredType declaredType = (DeclaredType)returnType;
                                boolean valid = false;
                                for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                                    if (typeArgument instanceof TypeVariable) {
                                        constrainedTypeParameters.add((TypeParameterElement)((TypeVariable)typeArgument).asElement());
                                        valid = true;
                                    }
                                }
                                if (!valid) {
                                    processingEnv.getMessager().printMessage(Kind.ERROR, "Method does not return a valid type token", method);
                                    continue typeLoop;
                                }
                            }
                            out.print("    ");
                            if (type.getModifiers().contains(Modifier.PUBLIC)) {
                                out.print("public ");
                            }
                            out.print("static <");
                            boolean first = true;
                            for (TypeParameterElement typeParameter : type.getTypeParameters()) {
                                if (constrainedTypeParameters.contains(typeParameter)) {
                                    if (first) {
                                        first = false;
                                    } else {
                                        out.print(",");
                                    }
                                    out.print(typeParameter);
                                }
                            }
                            out.print("> ");
                            String returnType;
                            {
                                StringBuilder buffer = new StringBuilder(type.getSimpleName());
                                buffer.append("<");
                                first = true;
                                for (TypeParameterElement typeParameter : type.getTypeParameters()) {
                                    if (first) {
                                        first = false;
                                    } else {
                                        buffer.append(",");
                                    }
                                    if (constrainedTypeParameters.contains(typeParameter)) {
                                        buffer.append(typeParameter);
                                    } else {
                                        buffer.append("?");
                                    }
                                }
                                buffer.append(">");
                                returnType = buffer.toString();
                            }
                            out.print(returnType);
                            out.print(" cast(");
                            out.print(type.getSimpleName());
                            out.print("<");
                            for (int i=0; i<type.getTypeParameters().size(); i++) {
                                if (i>0) {
                                    out.print(",");
                                }
                                out.print("?");
                            }
                            out.print("> o");
                            int i = 0;
                            for (ExecutableElement method : typeEntry.getValue()) {
                                out.print(", ");
                                out.print(method.getReturnType());
                                out.print(" token");
                                out.print(++i);
                            }
                            out.println(") {");
                            i = 1;
                            for (ExecutableElement method : typeEntry.getValue()) {
                                out.print("        if (token");
                                out.print(i);
                                out.print(" == null || o.");
                                out.print(method.getSimpleName());
                                out.print("() != token");
                                out.print(i);
                                out.println(") {");
                                out.println("            throw new ClassCastException();");
                                out.println("        }");
                                i++;
                            }
                            out.print("        return (");
                            out.print(returnType);
                            out.println(")o;");
                            out.println("    }");
                        }
                        out.println("}");
                    } catch (IOException ex) {
                        processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write source file");
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(TYPE_TOKEN_ANNOTATION_NAME);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
