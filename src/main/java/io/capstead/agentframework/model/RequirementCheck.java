package io.capstead.agentframework.model;
import java.util.List;
public record RequirementCheck(String requirement,ValidationStatus status,List<String> evidence,String reason) {}
