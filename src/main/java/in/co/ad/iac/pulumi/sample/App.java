package in.co.ad.iac.pulumi.sample;

import com.pulumi.Pulumi;
import com.pulumi.asset.FileAsset;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.storage.*;
import com.pulumi.azurenative.storage.enums.Kind;
import com.pulumi.azurenative.storage.enums.SkuName;
import com.pulumi.azurenative.storage.inputs.ListStorageAccountKeysArgs;
import com.pulumi.azurenative.storage.inputs.SkuArgs;
import com.pulumi.azurenative.storage.outputs.EndpointsResponse;
import com.pulumi.core.Output;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var resourceGroup = new ResourceGroup("resourceGroup");
            var storageAccount = new StorageAccount("sa", StorageAccountArgs.builder()
                    .resourceGroupName(resourceGroup.name())
                    .sku(SkuArgs.builder()
                            .name(SkuName.Standard_LRS)
                            .build())
                    .kind(Kind.StorageV2)
                    .build());

            var staticWebsite = new StorageAccountStaticWebsite("staticWebsite",
                    StorageAccountStaticWebsiteArgs.builder()
                            .accountName(storageAccount.name())
                            .resourceGroupName(resourceGroup.name())
                            .indexDocument("index.html")
                            .build());

            // Upload the file
            var index_html = new Blob("index.html", BlobArgs.builder()
                    .resourceGroupName(resourceGroup.name())
                    .accountName(storageAccount.name())
                    .containerName(staticWebsite.containerName())
                    .source(new FileAsset("index.html"))
                    .contentType("text/html")
                    .build());

            var primaryStorageKey = getStorageAccountPrimaryKey(
                    resourceGroup.name(),
                    storageAccount.name());

            ctx.export("primaryStorageKey", primaryStorageKey);
            ctx.export("staticEndpoint", storageAccount.primaryEndpoints()
                    .applyValue(EndpointsResponse::web));
        });
    }

    private static Output<String> getStorageAccountPrimaryKey(Output<String> resourceGroupName,
                                                              Output<String> accountName) {
        return StorageFunctions.listStorageAccountKeys(ListStorageAccountKeysArgs.builder()
                                                       .resourceGroupName(resourceGroupName)
                                                       .accountName(accountName)
                                                       .build())
            .applyValue(r -> r.keys().get(0).value())
            .asSecret();
    }
}
