package in.co.ad.iac.pulumi.sample;

import com.pulumi.Pulumi;
import com.pulumi.azurenative.network.Subnet;
import com.pulumi.azurenative.network.SubnetArgs;
import com.pulumi.azurenative.network.VirtualNetwork;
import com.pulumi.azurenative.network.VirtualNetworkArgs;
import com.pulumi.azurenative.network.inputs.AddressSpaceArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
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

            @SuppressWarnings("unchecked")
            Map<String, String> resourceGroupMap = rg.orElseThrow(() -> new NoSuchElementException("Resource Group mapping not found."));
            List<String> rgNames = new ArrayList<>(resourceGroupMap.values());

            @SuppressWarnings("unchecked")
            List<ResourceGroup> rgList = createRgList(rgNames, location, tags);

            ctx.output(rgList);

            var vnetConfig = config.getObject("vnet", Map.class);
            String vnetName = vnetConfig.isPresent() ? String.valueOf(vnetConfig.get().get("name")) : environment + "-vnet";
            var vnetAddressSpaceOpt = Optional.ofNullable(vnetConfig.isPresent() && vnetConfig.get().containsKey("address_space") ? vnetConfig.get().get("address_space") : null);
            String vnetRg = vnetConfig.isPresent() ? String.valueOf(vnetConfig.get().get("rg")) : rgList.get(0).name().toString();
            @SuppressWarnings("unchecked")
            List<String> vnetAddressSpace = (List<String>) vnetAddressSpaceOpt.orElseThrow(() -> new NoSuchElementException("No vnet address_space value found in the config."));

            @SuppressWarnings("unchecked")
            var vnet = createVnet(vnetName, vnetAddressSpace, location, vnetRg, tags, getResources(rgList));

            ctx.output(vnet);

            var subnetConfig = config.getObject("subnet", Map.class);
            Optional<String> nwRgOpt = resourceGroupMap.entrySet().stream().filter(rgLocal -> rgLocal.getKey().equals("nw")).map(Map.Entry::getValue).findFirst();
            String nwRg = nwRgOpt.orElseThrow(() -> new NoSuchElementException("nw resource group not found."));
            @SuppressWarnings("unchecked")
            Map<String, List<String>> subnetConfigMap = subnetConfig.orElseThrow(() -> new NoSuchElementException("Subnet value not found in the config."));
            Optional<ResourceGroup> nwResourceGroupOpt = rgList.stream().filter(rgLocal -> rgLocal.pulumiResourceName().equals(nwRg)).findFirst();
            ResourceGroup nwResourceGroup = nwResourceGroupOpt.orElseThrow(() -> new IllegalStateException("nw Resource Group not found after creating resources."));
            List<Resource> dependsOnList = getResources(List.of(vnet), rgList);

            List<Subnet> subnets = subnetConfigMap.entrySet().stream().map(entry -> createSubnet(entry.getKey(), entry.getValue(), nwResourceGroup, vnet, dependsOnList)).collect(Collectors.toList());

            ctx.output(subnets);
        });
    }

    private static List<ResourceGroup> createRgList(List<String> rgs, String location, Map<String, String> tags) {
        return rgs.stream().map(s -> createRg(s, location, tags)).collect(Collectors.toList());
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
        return Arrays.stream(dependsOn).flatMap(Collection::stream).map(x -> (Resource) x).collect(Collectors.toList());
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
