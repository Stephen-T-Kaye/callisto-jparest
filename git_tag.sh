version=

mkdir /root/.ssh/ && echo "$SSH_KEY" > /root/.ssh/id_rsa && chmod 0600 /root/.ssh/id_rsa
ssh-keyscan github.com >> /root/.ssh/known_hosts && chmod 600 /root/.ssh/known_hosts
printf  "Host github.com\n   Hostname github.com\n   IdentityFile /root/.ssh/id_rsa\n" > /root/.ssh/config
chmod 0600 /root/.ssh/config
git remote add tag-origin  git@github.com::Stephen-T-Kaye/callisto-jparest.git
git tag $version
git push tag-origin $version


