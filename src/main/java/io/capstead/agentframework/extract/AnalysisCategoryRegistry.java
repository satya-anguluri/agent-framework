package io.capstead.agentframework.extract;

import io.capstead.agentframework.model.AnalysisCategory;
import java.util.*;

public final class AnalysisCategoryRegistry {
    private final List<EvidenceCategoryResolver> resolvers;

    public AnalysisCategoryRegistry(){
        List<EvidenceCategoryResolver> loaded=new ArrayList<>();
        ServiceLoader.load(EvidenceCategoryResolver.class).forEach(loaded::add);
        loaded.add(new BuiltInEvidenceCategoryResolver());
        resolvers=List.copyOf(loaded);
    }

    public AnalysisCategory classify(String kind,String sourcePath){
        for(EvidenceCategoryResolver resolver:resolvers){
            Optional<AnalysisCategory> category=resolver.classify(kind,sourcePath);
            if(category.isPresent())return category.get();
        }
        return AnalysisCategory.OTHER;
    }
}
