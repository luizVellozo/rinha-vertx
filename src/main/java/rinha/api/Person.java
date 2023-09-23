package rinha.api;

import io.vertx.core.json.JsonArray;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

public class Person {

  String apelido;
  String nome;
  String nascimento;
  String[] stack;

  final static Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  public boolean isValid() {
    if (nome == null || nome.isBlank() || nome.length() > 100) {
     return false;
    }
    if (apelido == null || apelido.isBlank() || apelido.length() > 32) {
      return false;
    }
    if (nascimento == null || nascimento.isBlank() || !pattern.matcher(nascimento).matches()) {
      return false;
    }
    if (stack != null) {
      for (String value : stack) {
        if (value.isBlank() && value.length() > 32)
          return false;
      }
    }

    return true;
  }

  public void setStack(JsonArray stack) {

    this.stack = stack != null && !stack.isEmpty()
      ? stack.stream()
        .filter(Objects::nonNull)
        .map(Object::toString)
        .toArray(String[]::new)
      : null;

  }

  public String getStackInString() {
    if (stack == null || stack.length == 0) {
      return null;
    }

    StringJoiner joiner = new StringJoiner(",");
    for (String value : stack) {
      joiner.add(value);
    }

    return joiner.toString();
  }

  public String getId() {
    return UUID.randomUUID().toString();
  }
}
