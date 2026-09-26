# Signing PowerShell integration scripts

PowerShell integration scripts in this directory are Authenticode-signed. Any change to a `*.ps1` file invalidates its signature, so re-sign the changed files before merging.

1. Remove the existing signature block from every changed script. The block starts with:

   ```powershell
   # SIG # Begin signature block
   ```

   Delete that line and everything after it, through the end of the file.

2. Commit the script changes and push the branch to the remote repository.

3. Open the [Sign PowerShell Resources TeamCity configuration](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_SignPowershellResources). Select your branch and click **Run**.

4. Wait for the build to finish. Signing usually takes one to five minutes, depending on the number and size of the changed files.

5. Open the completed build, go to **Artifacts**, and click **Download all**.

6. Extract `signed-powershell-files.zip` and replace the changed scripts in your checkout with their signed versions.

7. Amend the signed scripts to the commit that contains the script changes, then push the amended commit.

Do not modify the scripts after signing. Any further change invalidates the signature and requires repeating this process.
