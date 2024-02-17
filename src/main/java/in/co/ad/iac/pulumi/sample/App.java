package in.co.ad.iac.pulumi.sample;

import com.pulumi.Pulumi;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var config = ctx.config();

            String location = config.get("location").orElseThrow();
            String environment = config.get("env").orElseThrow();
            var tags = config.getObject("tags", Map.class).orElseGet(() -> Map.of("env", environment));
            var rg = config.getObject("rg", Map.class);

            if (rg.isPresent()) {
                @SuppressWarnings("unchecked")
                var rgList = createRg(rg.get(), location, tags);
                ctx.output(rgList);
            }

        });
    }

    private static  List<ResourceGroup> createRg(Map<String, String> rgs, String location, Map<String, String> tags) {
        return rgs.values().stream().map(s -> createRg(s, location, tags)).collect(Collectors.toList());
    }


    private static ResourceGroup createRg(String name, String location, Map<String, String> tags) {
        return new ResourceGroup(name, ResourceGroupArgs.builder().resourceGroupName(name).location(location).tags(tags).build());
    }
}
