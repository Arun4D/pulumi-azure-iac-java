package in.co.ad.iac.pulumi.sample;

import com.pulumi.Pulumi;
import com.pulumi.azurenative.network.Subnet;
import com.pulumi.azurenative.network.SubnetArgs;
import com.pulumi.azurenative.network.VirtualNetwork;
import com.pulumi.azurenative.network.VirtualNetworkArgs;
import com.pulumi.azurenative.network.inputs.AddressSpaceArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.Resource;

import java.util.*;
import java.util.stream.Collectors;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var config = ctx.config();

            String location = config.get("location").orElseThrow();
            String environment = config.get("env").orElseThrow();
            var tags = config.getObject("tags", Map.class).orElseGet(() -> Map.of("env", environment));
            var rg = config.getObject("rg", Map.class);

            var rgConfig = rg.orElseThrow(() -> new IllegalStateException("Resource Group Config not present"));

            List<ResourceGroup> rgList = createRgList(rgConfig, location, tags);
            ctx.output(rgList);

            var vnetConfig = config.getObject("vnet", Map.class);
            String vnetName = vnetConfig.isPresent() ? String.valueOf(vnetConfig.get().get("name")) : environment + "-vnet";
            var vnetAddressSpaceOpt = Optional.ofNullable(vnetConfig.isPresent() && vnetConfig.get().containsKey("address_space") ? vnetConfig.get().get("address_space") : null);
            String vnetRg = vnetConfig.isPresent() ? String.valueOf(vnetConfig.get().get("rg")) : rgList.get(0).name().toString();

            @SuppressWarnings("unchecked")
            List<String> vnetAddressSpace = (List<String>) vnetAddressSpaceOpt.orElseThrow(() -> new NoSuchElementException("No vnet address_space value present"));
            var vnet = createVnet(vnetName, vnetAddressSpace, location, vnetRg, tags, getResources(rgList));
            ctx.output(vnet);

            var subnetConfig = config.getObject("subnet", Map.class);
            String nwRg = String.valueOf(rg.get().get("nw"));
            Map<String, List<String>> subnetConfigMap = subnetConfig.isPresent() ? subnetConfig.get() : null;
            Optional<ResourceGroup> nwResourceGroupOpt = rgList.stream().filter(x -> x.name().equals(nwRg)).findFirst();
            ResourceGroup nwResourceGroup = nwResourceGroupOpt.orElseThrow(() -> new IllegalStateException("nw Resource Group  not present"));
            List<Subnet> subnets = subnetConfigMap.entrySet().stream().map(entry -> createSubnet(entry.getKey(), entry.getValue(), nwResourceGroup, vnet, getResources((List<? extends Resource>) List.of(vnet), rgList))).collect(Collectors.toList());
            ctx.output(subnets);
        });
    }

    private static List<ResourceGroup> createRgList(Map<String, String> rgs, String location, Map<String, String> tags) {
        return rgs.values().stream().map(s -> createRg(s, location, tags)).collect(Collectors.toList());
    }


    private static ResourceGroup createRg(String name, String location, Map<String, String> tags) {
        return new ResourceGroup(name, ResourceGroupArgs.builder().resourceGroupName(name).location(location).tags(tags).build());
    }

    private static VirtualNetwork createVnet(String name, List<String> addressSpaces, String location, String rgName, Map<String, String> tags, List<Resource> dependsOn) {

        return new VirtualNetwork(name, VirtualNetworkArgs.builder()
                .addressSpace(AddressSpaceArgs.builder().addressPrefixes(addressSpaces).build())
                .location(location)
                .resourceGroupName(rgName)
                .tags(tags)
                .build(), CustomResourceOptions.builder().dependsOn(dependsOn).build());
    }

    @SafeVarargs
    private static List<Resource> getResources(List<? extends Resource>... dependsOn) {
        return Arrays.stream(dependsOn).flatMap(Collection::stream).map(x-> (Resource)x).collect(Collectors.toList());
    }

    private static Subnet createSubnet(String name, List<String> subnetAddress, ResourceGroup rg, VirtualNetwork vnet, List<Resource> dependsOn) {

        return new Subnet(name + "-snet",
                SubnetArgs.builder()
                        .addressPrefixes(subnetAddress)
                        .resourceGroupName(rg.name())
                        .subnetName(name)
                        .virtualNetworkName(vnet.name())
                        .build(),
                CustomResourceOptions.builder()
                        .dependsOn(dependsOn).build()
        );
    }
}
