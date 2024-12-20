provider "aws" {
  region  = var.region
  profile = var.aws_profile # .aws/credentials
}

# DAVE - add in new test case names here
variable "hostnames" {
  default = [
    "wrf_testcase_0.sh", "wrf_testcase_1.sh", "wrf_testcase_2.sh", "wrf_testcase_3.sh", 
    "wrf_testcase_4.sh", "wrf_testcase_5.sh", "wrf_testcase_6.sh", "wrf_testcase_7.sh", 
    "wrf_testcase_8.sh", "wrf_testcase_9.sh", "wrf_testcase_10.sh", "wrf_testcase_11.sh", 
    "wrf_testcase_12.sh", "wrf_testcase_13.sh", "wrf_testcase_14.sh", "wrf_testcase_15.sh", 
    "wrf_testcase_16.sh", "wrf_testcase_17.sh", "wrf_testcase_18.sh", "wrf_testcase_19.sh", 
    "wrf_testcase_20.sh", "wrf_testcase_21.sh", "wrf_testcase_22.sh", "wrf_testcase_23.sh", 
    "wrf_testcase_24.sh", "wrf_testcase_25.sh", "wrf_testcase_26.sh", "wrf_testcase_27.sh", 
    "wrf_testcase_28.sh", "wrf_testcase_29.sh", "wrf_testcase_30.sh", "wrf_testcase_31.sh", 
    "wrf_testcase_32.sh", "wrf_testcase_33.sh", "wrf_testcase_34.sh", "wrf_testcase_35.sh", 
    "wrf_testcase_36.sh", "wrf_testcase_37.sh", "wrf_testcase_38.sh", "wrf_testcase_39.sh", 
    "wrf_testcase_40.sh", "wrf_testcase_41.sh", "wrf_testcase_42.sh", "wrf_testcase_43.sh", 
    "wrf_testcase_44.sh", "wrf_testcase_45.sh", "wrf_testcase_46.sh", "wrf_testcase_47.sh", 
    "wrf_testcase_48.sh", "wrf_testcase_49.sh", "wrf_testcase_50.sh", "wrf_testcase_51.sh", 
    "wrf_testcase_52.sh", "wrf_testcase_53.sh", "wrf_testcase_54.sh", "wrf_testcase_55.sh", 
    "wrf_testcase_56.sh", "wrf_testcase_57.sh", "wrf_testcase_58.sh", "wrf_testcase_59.sh", 
    "wrf_testcase_60.sh"
  ]
}

data "template_file" "user-data" {
  count    = length(var.hostnames)
  template = file(element(var.hostnames, count.index))
}

resource "aws_instance" "application" {
  count                         = var.instance_count
  ami                           = var.ami
  iam_instance_profile          = var.instance_profile

  ebs_block_device {
    device_name = var.devicename
    volume_size = var.volumesize
  }

  availability_zone             = var.availability_zone
  ebs_optimized                 = var.ebs_optimized
  instance_type                 = var.instance_type_1
  key_name                      = var.key_name
  monitoring                    = var.monitoring
  vpc_security_group_ids        = var.security_group_ids
  subnet_id                     = var.subnet_id
  user_data                     = element(data.template_file.user-data.*.rendered, count.index)

  tags = merge(
    var.tags, 
    map(
      "Name", format("%s_%s", element(split(".", element(var.hostnames, count.index)), 0), var.instance_name),
      "UserDataFile", element(var.hostnames, count.index)
    )
  )
}
